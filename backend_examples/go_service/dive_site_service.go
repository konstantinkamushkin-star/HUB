package main

import (
	"context"
	"crypto/md5"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/go-redis/redis/v9"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// DiveSiteService handles all dive site operations
type DiveSiteService struct {
	db    *pgxpool.Pool
	redis *redis.Client
}

// DiveSiteFilters represents filter criteria
type DiveSiteFilters struct {
	DifficultyLevel *int     `json:"difficulty_level"`
	SiteTypes       []string `json:"site_types"`
	MinDepth        *float64 `json:"min_depth"`
	MaxDepth        *float64 `json:"max_depth"`
	MinRating       *float64 `json:"min_rating"`
	AccessTypes     []string `json:"access_types"`
	Country         *string  `json:"country"`
}

// SearchRequest represents a dive site search request
type SearchRequest struct {
	Lat     float64         `json:"lat" binding:"required"`
	Lng     float64         `json:"lng" binding:"required"`
	Radius  int             `json:"radius"` // meters
	Filters DiveSiteFilters `json:"filters"`
	SortBy  string          `json:"sort"` // distance, rating, popularity, newest
	Limit   int             `json:"limit"`
	Cursor  string          `json:"cursor"`
}

// DiveSiteListItem is the DTO for list responses
type DiveSiteListItem struct {
	ID            string    `json:"id"`
	Name          string    `json:"name"`
	Latitude      float64   `json:"latitude"`
	Longitude     float64   `json:"longitude"`
	DistanceMeters int      `json:"distance_meters,omitempty"`
	SiteTypes     []string  `json:"site_types"`
	Difficulty    int       `json:"difficulty_level"`
	DepthMin      *float64  `json:"depth_min,omitempty"`
	DepthMax      *float64  `json:"depth_max,omitempty"`
	Rating        float64   `json:"average_rating"`
	ReviewCount   int       `json:"review_count"`
	Country       string    `json:"country,omitempty"`
	Region        string    `json:"region,omitempty"`
	ThumbnailURL  *string   `json:"thumbnail_url,omitempty"`
}

// CachedResult represents cached search results
type CachedResult struct {
	Sites      []DiveSiteListItem `json:"sites"`
	NextCursor string             `json:"next_cursor"`
}

// NewDiveSiteService creates a new dive site service
func NewDiveSiteService(db *pgxpool.Pool, redis *redis.Client) *DiveSiteService {
	return &DiveSiteService{
		db:    db,
		redis: redis,
	}
}

// SearchDiveSites performs a geospatial search with filters
func (s *DiveSiteService) SearchDiveSites(ctx context.Context, req SearchRequest) ([]DiveSiteListItem, string, error) {
	// Set defaults
	if req.Radius == 0 {
		req.Radius = 50000 // 50km default
	}
	if req.Limit == 0 {
		req.Limit = 20
	}
	if req.Limit > 100 {
		req.Limit = 100 // Max limit
	}
	if req.SortBy == "" {
		req.SortBy = "distance"
	}

	// 1. Check cache
	cacheKey := s.generateCacheKey(req)
	cached, err := s.redis.Get(ctx, cacheKey).Result()
	if err == nil {
		var result CachedResult
		if err := json.Unmarshal([]byte(cached), &result); err == nil {
			log.Printf("Cache HIT for key: %s", cacheKey)
			return result.Sites, result.NextCursor, nil
		}
	}

	log.Printf("Cache MISS for key: %s", cacheKey)

	// 2. Query database
	sites, nextCursor, err := s.searchInDB(ctx, req)
	if err != nil {
		return nil, "", fmt.Errorf("database query failed: %w", err)
	}

	// 3. Cache result
	ttl := s.getCacheTTL(req.Radius)
	result := CachedResult{
		Sites:      sites,
		NextCursor: nextCursor,
	}
	data, err := json.Marshal(result)
	if err == nil {
		s.redis.Set(ctx, cacheKey, data, ttl)
	}

	return sites, nextCursor, nil
}

// searchInDB performs the actual database query
func (s *DiveSiteService) searchInDB(ctx context.Context, req SearchRequest) ([]DiveSiteListItem, string, error) {
	// Parse cursor
	var cursorDistance float64
	var cursorID string
	if req.Cursor != "" {
		fmt.Sscanf(req.Cursor, "%f|%s", &cursorDistance, &cursorID)
	}

	query := `
		WITH geo_filtered AS (
			SELECT 
				id,
				name,
				latitude,
				longitude,
				site_types,
				difficulty_level,
				depth_min,
				depth_max,
				average_rating,
				review_count,
				country,
				region,
				photo_urls,
				ST_Distance(
					location,
					ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
				) as distance_meters
			FROM dive_sites
			WHERE is_active = true
			  AND ST_DWithin(
				  location,
				  ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
				  $3
			  )
			  AND ($13 = '' OR (distance_meters, id::text) > ($14, $13))
		),
		filtered AS (
			SELECT *
			FROM geo_filtered
			WHERE 
				($4::INTEGER IS NULL OR difficulty_level = $4)
				AND ($5::TEXT[] IS NULL OR site_types && $5)
				AND ($6::DOUBLE PRECISION IS NULL OR depth_max >= $6)
				AND ($7::DOUBLE PRECISION IS NULL OR depth_min <= $7)
				AND ($8::DOUBLE PRECISION IS NULL OR average_rating >= $8)
				AND ($9::TEXT[] IS NULL OR access_type && $9)
				AND ($10::TEXT IS NULL OR country = $10)
		)
		SELECT 
			id,
			name,
			latitude,
			longitude,
			site_types,
			difficulty_level,
			depth_min,
			depth_max,
			average_rating,
			review_count,
			country,
			region,
			photo_urls,
			ROUND(distance_meters::numeric, 0)::INTEGER as distance_meters
		FROM filtered
		ORDER BY 
			CASE 
				WHEN $11 = 'distance' THEN distance_meters
				WHEN $11 = 'rating' THEN -average_rating
				WHEN $11 = 'popularity' THEN -review_count
				ELSE -EXTRACT(EPOCH FROM created_at)
			END,
			id
		LIMIT $12 + 1
	`

	// Prepare arguments
	args := []interface{}{
		req.Lng, req.Lat, req.Radius,
		req.Filters.DifficultyLevel,
		s.toTextArray(req.Filters.SiteTypes),
		req.Filters.MinDepth,
		req.Filters.MaxDepth,
		req.Filters.MinRating,
		s.toTextArray(req.Filters.AccessTypes),
		req.Filters.Country,
		req.SortBy,
		req.Limit,
		cursorID,
		cursorDistance,
	}

	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, "", err
	}
	defer rows.Close()

	var sites []DiveSiteListItem
	var nextCursor string
	count := 0

	for rows.Next() {
		if count >= req.Limit {
			// There's more data - create cursor from last row
			var lastSite DiveSiteListItem
			var photoURLs []string
			err := rows.Scan(
				&lastSite.ID, &lastSite.Name,
				&lastSite.Latitude, &lastSite.Longitude,
				&lastSite.SiteTypes, &lastSite.Difficulty,
				&lastSite.DepthMin, &lastSite.DepthMax,
				&lastSite.Rating, &lastSite.ReviewCount,
				&lastSite.Country, &lastSite.Region,
				&photoURLs,
				&lastSite.DistanceMeters,
			)
			if err == nil {
				nextCursor = fmt.Sprintf("%.2f|%s", float64(lastSite.DistanceMeters), lastSite.ID)
			}
			break
		}

		var site DiveSiteListItem
		var photoURLs []string
		err := rows.Scan(
			&site.ID, &site.Name,
			&site.Latitude, &site.Longitude,
			&site.SiteTypes, &site.Difficulty,
			&site.DepthMin, &site.DepthMax,
			&site.Rating, &site.ReviewCount,
			&site.Country, &site.Region,
			&photoURLs,
			&site.DistanceMeters,
		)
		if err != nil {
			log.Printf("Error scanning row: %v", err)
			continue
		}

		// Set thumbnail (first photo)
		if len(photoURLs) > 0 {
			site.ThumbnailURL = &photoURLs[0]
		}

		sites = append(sites, site)
		count++
	}

	if err := rows.Err(); err != nil {
		return nil, "", err
	}

	return sites, nextCursor, nil
}

// generateCacheKey creates a cache key from request parameters
func (s *DiveSiteService) generateCacheKey(req SearchRequest) string {
	filtersHash := s.hashFilters(req.Filters)
	return fmt.Sprintf(
		"divesites:geo:%d:%d:r%d:f%s:sort%s:limit%d:cursor%s",
		int(req.Lat*1000),  // Round to ~100m
		int(req.Lng*1000),
		req.Radius,
		filtersHash,
		req.SortBy,
		req.Limit,
		req.Cursor,
	)
}

// hashFilters creates an MD5 hash of filters for cache key
func (s *DiveSiteService) hashFilters(filters DiveSiteFilters) string {
	data, _ := json.Marshal(filters)
	hash := md5.Sum(data)
	return hex.EncodeToString(hash[:])[:8] // First 8 chars
}

// getCacheTTL returns cache TTL based on radius
func (s *DiveSiteService) getCacheTTL(radius int) time.Duration {
	if radius < 10000 { // < 10km - changes frequently
		return 5 * time.Minute
	} else if radius < 50000 { // < 50km
		return 15 * time.Minute
	}
	return 1 * time.Hour // > 50km - rarely changes
}

// toTextArray converts []string to PostgreSQL text array format
func (s *DiveSiteService) toTextArray(arr []string) interface{} {
	if len(arr) == 0 {
		return nil
	}
	return arr
}

// SearchByBoundingBox searches dive sites within a bounding box (for map)
func (s *DiveSiteService) SearchByBoundingBox(
	ctx context.Context,
	north, south, east, west float64,
	filters DiveSiteFilters,
	limit int,
) ([]DiveSiteListItem, error) {
	if limit == 0 {
		limit = 500 // Max for map
	}
	if limit > 500 {
		limit = 500
	}

	query := `
		SELECT 
			id,
			name,
			latitude,
			longitude,
			site_types,
			difficulty_level,
			depth_min,
			depth_max,
			average_rating,
			review_count,
			country,
			region,
			photo_urls
		FROM dive_sites
		WHERE is_active = true
		  AND location && ST_MakeEnvelope($1, $2, $3, $4, 4326)::geography
		  AND ($5::INTEGER IS NULL OR difficulty_level = $5)
		  AND ($6::TEXT[] IS NULL OR site_types && $6)
		  AND ($7::DOUBLE PRECISION IS NULL OR average_rating >= $7)
		LIMIT $8
	`

	args := []interface{}{
		west, south, east, north,
		filters.DifficultyLevel,
		s.toTextArray(filters.SiteTypes),
		filters.MinRating,
		limit,
	}

	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var sites []DiveSiteListItem
	for rows.Next() {
		var site DiveSiteListItem
		var photoURLs []string
		err := rows.Scan(
			&site.ID, &site.Name,
			&site.Latitude, &site.Longitude,
			&site.SiteTypes, &site.Difficulty,
			&site.DepthMin, &site.DepthMax,
			&site.Rating, &site.ReviewCount,
			&site.Country, &site.Region,
			&photoURLs,
		)
		if err != nil {
			continue
		}

		if len(photoURLs) > 0 {
			site.ThumbnailURL = &photoURLs[0]
		}

		sites = append(sites, site)
	}

	return sites, rows.Err()
}

// GetPopularDiveSites returns popular dive sites (fallback when no location)
func (s *DiveSiteService) GetPopularDiveSites(
	ctx context.Context,
	country string,
	limit int,
) ([]DiveSiteListItem, error) {
	if limit == 0 {
		limit = 20
	}
	if limit > 100 {
		limit = 100
	}

	query := `
		SELECT 
			id,
			name,
			latitude,
			longitude,
			site_types,
			difficulty_level,
			depth_min,
			depth_max,
			average_rating,
			review_count,
			country,
			region,
			photo_urls
		FROM dive_sites
		WHERE is_active = true
		  AND ($1::TEXT IS NULL OR country = $1)
		  AND review_count >= 10
		ORDER BY 
			(average_rating * LN(review_count + 1)) DESC,
			review_count DESC
		LIMIT $2
	`

	args := []interface{}{country, limit}

	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var sites []DiveSiteListItem
	for rows.Next() {
		var site DiveSiteListItem
		var photoURLs []string
		err := rows.Scan(
			&site.ID, &site.Name,
			&site.Latitude, &site.Longitude,
			&site.SiteTypes, &site.Difficulty,
			&site.DepthMin, &site.DepthMax,
			&site.Rating, &site.ReviewCount,
			&site.Country, &site.Region,
			&photoURLs,
		)
		if err != nil {
			continue
		}

		if len(photoURLs) > 0 {
			site.ThumbnailURL = &photoURLs[0]
		}

		sites = append(sites, site)
	}

	return sites, rows.Err()
}

// InvalidateCache invalidates cache for a specific dive site
func (s *DiveSiteService) InvalidateCache(ctx context.Context, siteID string) error {
	// Find all cache keys that might contain this site
	// This is a simplified version - in production, maintain a mapping
	pattern := "divesites:*"
	keys, err := s.redis.Keys(ctx, pattern).Result()
	if err != nil {
		return err
	}

	// Delete matching keys (in production, use a more efficient strategy)
	for _, key := range keys {
		// Check if this cache entry contains the site
		// For simplicity, we'll delete all geo caches (can be optimized)
		if strings.Contains(key, "divesites:geo:") {
			s.redis.Del(ctx, key)
		}
	}

	return nil
}
