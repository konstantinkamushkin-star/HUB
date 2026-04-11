package main

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

// SetupRoutes configures all API routes
func SetupRoutes(service *DiveSiteService) *gin.Engine {
	r := gin.Default()

	// Middleware
	r.Use(CORSMiddleware())
	r.Use(PerformanceMiddleware())
	r.Use(CompressionMiddleware())

	// Health check
	r.GET("/health", HealthCheckHandler(service))

	// API v1
	v1 := r.Group("/api/v1")
	{
		// Search dive sites by location
		v1.GET("/dive-sites/search", SearchDiveSitesHandler(service))

		// Search by bounding box (for map)
		v1.GET("/dive-sites/map", MapSearchHandler(service))

		// Popular dive sites (fallback)
		v1.GET("/dive-sites/popular", PopularDiveSitesHandler(service))

		// Clusters (for map clustering)
		v1.GET("/dive-sites/clusters", ClustersHandler(service))
	}

	return r
}

// SearchDiveSitesHandler handles GET /api/v1/dive-sites/search
func SearchDiveSitesHandler(service *DiveSiteService) gin.HandlerFunc {
	return func(c *gin.Context) {
		var req SearchRequest

		// Parse query parameters
		lat, err := strconv.ParseFloat(c.Query("lat"), 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid lat parameter"})
			return
		}
		req.Lat = lat

		lng, err := strconv.ParseFloat(c.Query("lng"), 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid lng parameter"})
			return
		}
		req.Lng = lng

		// Optional parameters
		if radiusStr := c.Query("radius"); radiusStr != "" {
			if radius, err := strconv.Atoi(radiusStr); err == nil {
				req.Radius = radius
			}
		}

		if difficultyStr := c.Query("difficulty"); difficultyStr != "" {
			if difficulty, err := strconv.Atoi(difficultyStr); err == nil {
				req.Filters.DifficultyLevel = &difficulty
			}
		}

		if siteTypesStr := c.Query("site_types"); siteTypesStr != "" {
			req.Filters.SiteTypes = strings.Split(siteTypesStr, ",")
		}

		if minDepthStr := c.Query("min_depth"); minDepthStr != "" {
			if minDepth, err := strconv.ParseFloat(minDepthStr, 64); err == nil {
				req.Filters.MinDepth = &minDepth
			}
		}

		if maxDepthStr := c.Query("max_depth"); maxDepthStr != "" {
			if maxDepth, err := strconv.ParseFloat(maxDepthStr, 64); err == nil {
				req.Filters.MaxDepth = &maxDepth
			}
		}

		if minRatingStr := c.Query("min_rating"); minRatingStr != "" {
			if minRating, err := strconv.ParseFloat(minRatingStr, 64); err == nil {
				req.Filters.MinRating = &minRating
			}
		}

		if accessTypesStr := c.Query("access_type"); accessTypesStr != "" {
			req.Filters.AccessTypes = strings.Split(accessTypesStr, ",")
		}

		if country := c.Query("country"); country != "" {
			req.Filters.Country = &country
		}

		if sort := c.Query("sort"); sort != "" {
			req.SortBy = sort
		}

		if limitStr := c.Query("limit"); limitStr != "" {
			if limit, err := strconv.Atoi(limitStr); err == nil {
				req.Limit = limit
			}
		}

		if cursor := c.Query("cursor"); cursor != "" {
			req.Cursor = cursor
		}

		// Perform search
		sites, nextCursor, err := service.SearchDiveSites(c.Request.Context(), req)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error": "Failed to search dive sites",
				"details": err.Error(),
			})
			return
		}

		// Response
		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"data": sites,
			"pagination": gin.H{
				"has_more": nextCursor != "",
				"next_cursor": nextCursor,
				"limit": req.Limit,
			},
		})
	}
}

// MapSearchHandler handles GET /api/v1/dive-sites/map
func MapSearchHandler(service *DiveSiteService) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Parse bounding box
		north, _ := strconv.ParseFloat(c.Query("north"), 64)
		south, _ := strconv.ParseFloat(c.Query("south"), 64)
		east, _ := strconv.ParseFloat(c.Query("east"), 64)
		west, _ := strconv.ParseFloat(c.Query("west"), 64)

		if north == 0 || south == 0 || east == 0 || west == 0 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid bounding box"})
			return
		}

		// Parse filters
		var filters DiveSiteFilters
		if difficultyStr := c.Query("difficulty"); difficultyStr != "" {
			if difficulty, err := strconv.Atoi(difficultyStr); err == nil {
				filters.DifficultyLevel = &difficulty
			}
		}

		if siteTypesStr := c.Query("site_types"); siteTypesStr != "" {
			filters.SiteTypes = strings.Split(siteTypesStr, ",")
		}

		limit := 500
		if limitStr := c.Query("limit"); limitStr != "" {
			if l, err := strconv.Atoi(limitStr); err == nil && l > 0 && l <= 500 {
				limit = l
			}
		}

		// Search
		sites, err := service.SearchByBoundingBox(
			c.Request.Context(),
			north, south, east, west,
			filters,
			limit,
		)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error": "Failed to search dive sites",
			})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"data": sites,
		})
	}
}

// PopularDiveSitesHandler handles GET /api/v1/dive-sites/popular
func PopularDiveSitesHandler(service *DiveSiteService) gin.HandlerFunc {
	return func(c *gin.Context) {
		country := c.Query("country")
		limit := 20

		if limitStr := c.Query("limit"); limitStr != "" {
			if l, err := strconv.Atoi(limitStr); err == nil && l > 0 && l <= 100 {
				limit = l
			}
		}

		sites, err := service.GetPopularDiveSites(c.Request.Context(), country, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error": "Failed to get popular dive sites",
			})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"data": sites,
		})
	}
}

// ClustersHandler handles GET /api/v1/dive-sites/clusters
func ClustersHandler(service *DiveSiteService) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Parse bounding box
		north, _ := strconv.ParseFloat(c.Query("north"), 64)
		south, _ := strconv.ParseFloat(c.Query("south"), 64)
		east, _ := strconv.ParseFloat(c.Query("east"), 64)
		west, _ := strconv.ParseFloat(c.Query("west"), 64)

		zoom := 10
		if zoomStr := c.Query("zoom"); zoomStr != "" {
			if z, err := strconv.Atoi(zoomStr); err == nil {
				zoom = z
			}
		}

		// Get sites in bounding box
		var filters DiveSiteFilters
		sites, err := service.SearchByBoundingBox(
			c.Request.Context(),
			north, south, east, west,
			filters,
			1000, // Max for clustering
		)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{
				"error": "Failed to get clusters",
			})
			return
		}

		// Perform clustering (simplified - use proper clustering algorithm in production)
		clusters, points := ClusterDiveSites(sites, zoom, 0.01) // 0.01 degrees ~ 1km

		c.JSON(http.StatusOK, gin.H{
			"success": true,
			"clusters": clusters,
			"points": points,
		})
	}
}

// HealthCheckHandler handles GET /health
func HealthCheckHandler(service *DiveSiteService) gin.HandlerFunc {
	return func(c *gin.Context) {
		ctx := c.Request.Context()

		// Check database
		if err := service.db.Ping(ctx); err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{
				"status": "unhealthy",
				"db": "down",
			})
			return
		}

		// Check Redis
		if err := service.redis.Ping(ctx).Err(); err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{
				"status": "unhealthy",
				"redis": "down",
			})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"status": "healthy",
		})
	}
}

// Cluster represents a cluster of dive sites
type Cluster struct {
	ID       string  `json:"id"`
	Lat      float64 `json:"latitude"`
	Lng      float64 `json:"longitude"`
	Count    int     `json:"count"`
}

// ClusterDiveSites performs simple clustering (simplified version)
func ClusterDiveSites(sites []DiveSiteListItem, zoom int, distance float64) ([]Cluster, []DiveSiteListItem) {
	if zoom > 15 {
		// High zoom - return all points
		return nil, sites
	}

	if zoom < 10 {
		// Low zoom - return only clusters
		clusters := createClusters(sites, distance)
		return clusters, nil
	}

	// Medium zoom - mixed
	clusters, points := createMixedClusters(sites, distance, zoom)
	return clusters, points
}

func createClusters(sites []DiveSiteListItem, distance float64) []Cluster {
	clusters := []Cluster{}
	used := make(map[int]bool)

	for i, site := range sites {
		if used[i] {
			continue
		}

		cluster := Cluster{
			ID:    "cluster_" + site.ID,
			Lat:   site.Latitude,
			Lng:   site.Longitude,
			Count: 1,
		}

		for j := i + 1; j < len(sites); j++ {
			if used[j] {
				continue
			}

			dist := haversineDistance(
				site.Latitude, site.Longitude,
				sites[j].Latitude, sites[j].Longitude,
			)

			if dist <= distance {
				cluster.Count++
				used[j] = true
				// Update cluster center (average)
				cluster.Lat = (cluster.Lat*float64(cluster.Count-1) + sites[j].Latitude) / float64(cluster.Count)
				cluster.Lng = (cluster.Lng*float64(cluster.Count-1) + sites[j].Longitude) / float64(cluster.Count)
			}
		}

		clusters = append(clusters, cluster)
		used[i] = true
	}

	return clusters
}

func createMixedClusters(sites []DiveSiteListItem, distance float64, zoom int) ([]Cluster, []DiveSiteListItem) {
	// Simplified - in production use proper clustering algorithm (e.g., Supercluster)
	allClusters := createClusters(sites, distance)
	finalClusters := []Cluster{}
	points := []DiveSiteListItem{}

	// Separate clusters (count > 1) from single points (count = 1)
	for _, cluster := range allClusters {
		if cluster.Count > 1 {
			finalClusters = append(finalClusters, cluster)
		} else {
			// Find the site for this single-point cluster
			for _, site := range sites {
				// Approximate match (within small distance)
				dist := haversineDistance(site.Latitude, site.Longitude, cluster.Lat, cluster.Lng)
				if dist < 0.001 { // Very close
					points = append(points, site)
					break
				}
			}
		}
	}

	return finalClusters, points
}

// haversineDistance calculates distance between two points in degrees
func haversineDistance(lat1, lon1, lat2, lon2 float64) float64 {
	// Simplified - returns approximate distance in degrees
	// For production, use proper haversine formula
	dlat := lat2 - lat1
	dlon := lon2 - lon1
	return (dlat*dlat + dlon*dlon) * 111.0 // Rough conversion to km
}

// Middleware functions

func CORSMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, accept, origin, Cache-Control, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT, DELETE")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}

func PerformanceMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		duration := time.Since(start)
		c.Header("X-Response-Time", strconv.FormatInt(duration.Milliseconds(), 10)+"ms")
	}
}

func CompressionMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Gin automatically handles gzip if client supports it
		c.Next()
	}
}
