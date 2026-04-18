import { Injectable, Inject } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource } from 'typeorm';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Cache } from 'cache-manager';
import { DiveSiteEntity } from './entities/dive-site.entity';
import {
  SearchDiveSitesDto,
  MapSearchDto,
  PopularDiveSitesDto,
} from './dto/search-dive-sites.dto';
import {
  DiveSiteListItemDto,
  DiveSiteSearchResultDto,
  PaginationInfoDto,
  SearchMetaDto,
} from './dto/dive-site-response.dto';
import * as crypto from 'crypto';

@Injectable()
export class DiveSitesService {
  constructor(
    @InjectRepository(DiveSiteEntity)
    private diveSiteRepository: Repository<DiveSiteEntity>,
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
    private dataSource: DataSource,
  ) {}

  /**
   * Search dive sites by location with radius
   */
  async searchByLocation(
    searchDto: SearchDiveSitesDto,
  ): Promise<DiveSiteSearchResultDto> {
    const startTime = Date.now();

    // Generate cache key
    const cacheKey = this.generateCacheKey(searchDto);
    
    try {
      const cached = await this.cacheManager.get<DiveSiteSearchResultDto>(
        cacheKey,
      );

      if (cached) {
        console.log(`✅ Cache HIT for key: ${cacheKey}`);
        return cached;
      }
    } catch (error) {
      console.warn('Cache get error:', error.message);
    }

    console.log(`❌ Cache MISS for key: ${cacheKey}`);

    // Parse cursor if provided
    let cursorDistance = 0;
    let cursorId = '';
    if (searchDto.cursor) {
      const parts = searchDto.cursor.split('|');
      if (parts.length === 2) {
        cursorDistance = parseFloat(parts[0]) || 0;
        cursorId = parts[1];
      }
    }


    // Build query using raw SQL for better PostGIS support.
    // Coordinates are always taken from PostGIS location (ST_Y=latitude, ST_X=longitude)
    // so the map shows dive sites in the correct place regardless of table column state.
    const radius = searchDto.radius || 50000;
    const limit = searchDto.limit || 20;
    const lat = searchDto.lat;
    const lng = searchDto.lng;

    let query = `
      WITH geo_filtered AS (
        SELECT 
          id,
          name,
          ST_Y(location::geometry) AS latitude,
          ST_X(location::geometry) AS longitude,
          site_types,
          difficulty_level,
          depth_min,
          depth_max,
          average_rating,
          review_count,
          country,
          region,
          photo_urls,
          created_at,
          ST_Distance(
            location::geography,
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
          ) as distance_meters
        FROM dive_sites
        WHERE is_active = true
          AND ST_DWithin(
              location::geography,
              ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
              $3
          )
    `;

    const params: any[] = [lng, lat, radius];
    let paramIndex = 4;

    // Apply filters (cursor will be applied AFTER sorting)
    if (searchDto.difficulty) {
      query += ` AND difficulty_level = $${paramIndex}`;
      params.push(searchDto.difficulty);
      paramIndex++;
    }

    if (searchDto.site_types && searchDto.site_types.length > 0) {
      query += ` AND site_types && $${paramIndex}`;
      params.push(searchDto.site_types);
      paramIndex++;
    }

    if (searchDto.min_depth !== undefined) {
      query += ` AND depth_max >= $${paramIndex}`;
      params.push(searchDto.min_depth);
      paramIndex++;
    }

    if (searchDto.max_depth !== undefined) {
      query += ` AND depth_min <= $${paramIndex}`;
      params.push(searchDto.max_depth);
      paramIndex++;
    }

    if (searchDto.min_rating !== undefined) {
      query += ` AND average_rating >= $${paramIndex}`;
      params.push(searchDto.min_rating);
      paramIndex++;
    }

    if (searchDto.access_type && searchDto.access_type.length > 0) {
      query += ` AND access_type && $${paramIndex}`;
      params.push(searchDto.access_type);
      paramIndex++;
    }

    if (searchDto.country) {
      query += ` AND country = $${paramIndex}`;
      params.push(searchDto.country);
      paramIndex++;
    }

    query += `
      ),
      filtered AS (
        SELECT * FROM geo_filtered
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
        created_at,
        ROUND(distance_meters::numeric, 0)::INTEGER as distance_meters
      FROM filtered
    `;

    // Sorting
    const sortBy = searchDto.sort || 'distance';
    switch (sortBy) {
      case 'rating':
        query += ` ORDER BY average_rating DESC, id`;
        break;
      case 'popularity':
        query += ` ORDER BY review_count DESC, id`;
        break;
      case 'newest':
        query += ` ORDER BY created_at DESC, id`;
        break;
      case 'distance':
      default:
        query += ` ORDER BY distance_meters ASC, id`;
        break;
    }

    // Apply cursor AFTER sorting using subquery
    if (cursorId) {
      const sortField = sortBy === 'distance' ? 'distance_meters' : 
                       sortBy === 'rating' ? 'average_rating' : 
                       sortBy === 'popularity' ? 'review_count' : 'created_at';
      const comparison = sortBy === 'distance' ? '>' : '<';
      // Wrap in subquery to apply cursor filter after sorting
      query = `
        SELECT * FROM (
          ${query}
        ) sorted_results
        WHERE (${sortField}, id::text) ${comparison} ($${paramIndex}, $${paramIndex + 1})
        LIMIT $${paramIndex + 2}
      `;
      params.push(cursorDistance, cursorId, limit + 1);
      paramIndex += 3;
    } else {
      // Limit + 1 to check if there's more
      query += ` LIMIT $${paramIndex}`;
      params.push(limit + 1);
      paramIndex += 1;
    }

    // Execute query
    const results = await this.dataSource.query(query, params);

    // Process results
    const sites: DiveSiteListItemDto[] = results.slice(0, limit).map((row) => ({
      id: row.id,
      name: row.name,
      // Use high precision coordinates from PostGIS location if available, fallback to stored columns
      latitude: row.latitude ? parseFloat(parseFloat(row.latitude).toFixed(6)) : 0,
      longitude: row.longitude ? parseFloat(parseFloat(row.longitude).toFixed(6)) : 0,
      distance_meters: row.distance_meters,
      site_types: Array.isArray(row.site_types) ? row.site_types : [],
      difficulty_level: row.difficulty_level || 1,
      depth_min: row.depth_min ? parseFloat(row.depth_min) : undefined,
      depth_max: row.depth_max ? parseFloat(row.depth_max) : undefined,
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      region: row.region || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
    }));

    // Check if there's more
    const hasMore = results.length > limit;
    let nextCursor: string | undefined;

    if (hasMore && results.length > limit) {
      const lastRow = results[limit];
      nextCursor = `${lastRow.distance_meters}|${lastRow.id}`;
    }

    const queryTime = Date.now() - startTime;

    const result: DiveSiteSearchResultDto = {
      success: true,
      data: sites,
      pagination: {
        has_more: hasMore,
        next_cursor: nextCursor,
        limit,
      },
      meta: {
        query_time_ms: queryTime,
      },
    };

    // Cache result
    try {
      const ttl = this.getCacheTTL(radius);
      await this.cacheManager.set(cacheKey, result, ttl);
    } catch (error) {
      console.warn('Cache set error:', error.message);
    }

    return result;
  }

  /**
   * Search dive sites within bounding box (for map)
   */
  async searchByBounds(
    searchDto: MapSearchDto,
  ): Promise<DiveSiteListItemDto[]> {
    const cacheKey = this.generateBoundsCacheKey(searchDto);
    
    try {
      const cached = await this.cacheManager.get<DiveSiteListItemDto[]>(
        cacheKey,
      );

      if (cached) {
        return cached;
      }
    } catch (error) {
      console.warn('Cache get error:', error.message);
    }

    let query = `
      SELECT 
        id,
        name,
        ST_Y(location::geometry) AS latitude,
        ST_X(location::geometry) AS longitude,
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
        AND location IS NOT NULL
        AND location::geometry && ST_MakeEnvelope($1, $2, $3, $4, 4326)
    `;

    const params: any[] = [
      searchDto.west,
      searchDto.south,
      searchDto.east,
      searchDto.north,
    ];
    let paramIndex = 5;

    // Apply filters
    if (searchDto.difficulty) {
      query += ` AND difficulty_level = $${paramIndex}`;
      params.push(searchDto.difficulty);
      paramIndex++;
    }

    if (searchDto.site_types && searchDto.site_types.length > 0) {
      query += ` AND site_types && $${paramIndex}`;
      params.push(searchDto.site_types);
      paramIndex++;
    }

    if (searchDto.min_rating !== undefined) {
      query += ` AND average_rating >= $${paramIndex}`;
      params.push(searchDto.min_rating);
      paramIndex++;
    }

    query += ` LIMIT $${paramIndex}`;
    params.push(searchDto.limit || 500);

    const results = await this.dataSource.query(query, params);

    const sites: DiveSiteListItemDto[] = results.map((row) => ({
      id: row.id,
      name: row.name,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      site_types: Array.isArray(row.site_types) ? row.site_types : [],
      difficulty_level: row.difficulty_level || 1,
      depth_min: row.depth_min ? parseFloat(row.depth_min) : undefined,
      depth_max: row.depth_max ? parseFloat(row.depth_max) : undefined,
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      region: row.region || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
    }));

    // Cache result
    try {
      const ttl = this.getCacheTTL(50000);
      await this.cacheManager.set(cacheKey, sites, ttl);
    } catch (error) {
      console.warn('Cache set error:', error.message);
    }

    return sites;
  }

  /**
   * Paginated explore list with filters + total count (for mobile Explore list).
   */
  async listExplore(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    difficultyLevel?: number | string;
    diveTypes?: string | string[];
    minDepth?: number | string;
    maxDepth?: number | string;
    minRating?: number | string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    data: DiveSiteListItemDto[];
    total: number;
    page: number;
    limit: number;
  }> {
    const parseNum = (v: unknown, fallback: number): number => {
      if (v === undefined || v === null || v === '') return fallback;
      const n = Number(v);
      return Number.isFinite(n) ? n : fallback;
    };

    const page = Math.max(1, Math.floor(parseNum(raw.page, 1)));
    let limit = Math.floor(parseNum(raw.limit, 20));
    limit = Math.min(100, Math.max(1, limit));
    const offset = (page - 1) * limit;

    const sortRaw = (raw.sort || 'popularity').toLowerCase();
    const sort =
      sortRaw === 'distance' ||
      sortRaw === 'rating' ||
      sortRaw === 'name' ||
      sortRaw === 'reviews' ||
      sortRaw === 'popularity'
        ? sortRaw
        : 'popularity';

    let diveTypesArr: string[] = [];
    if (raw.diveTypes) {
      if (Array.isArray(raw.diveTypes)) {
        diveTypesArr = raw.diveTypes.map((s) => String(s).trim()).filter(Boolean);
      } else if (typeof raw.diveTypes === 'string') {
        diveTypesArr = raw.diveTypes
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean);
      }
    }

    let difficultyLevelNum: number | undefined;
    if (
      raw.difficultyLevel !== undefined &&
      raw.difficultyLevel !== null &&
      raw.difficultyLevel !== ''
    ) {
      const dn = parseNum(raw.difficultyLevel, NaN);
      if (!Number.isNaN(dn)) {
        difficultyLevelNum = Math.min(4, Math.max(1, Math.floor(dn)));
      }
    }

    const minDepth =
      raw.minDepth !== undefined && raw.minDepth !== null && raw.minDepth !== ''
        ? parseNum(raw.minDepth, NaN)
        : undefined;
    const maxDepth =
      raw.maxDepth !== undefined && raw.maxDepth !== null && raw.maxDepth !== ''
        ? parseNum(raw.maxDepth, NaN)
        : undefined;
    const minRating =
      raw.minRating !== undefined && raw.minRating !== null && raw.minRating !== ''
        ? parseNum(raw.minRating, NaN)
        : undefined;

    const userLat =
      raw.userLat !== undefined && raw.userLat !== null && raw.userLat !== ''
        ? parseNum(raw.userLat, NaN)
        : undefined;
    const userLng =
      raw.userLng !== undefined && raw.userLng !== null && raw.userLng !== ''
        ? parseNum(raw.userLng, NaN)
        : undefined;

    let q = (raw.q || '').trim();
    if (q.length > 200) q = q.slice(0, 200);

    let where = `
      WHERE is_active = true
        AND location IS NOT NULL
    `;
    const params: any[] = [];
    let p = 1;

    if (raw.country && String(raw.country).trim()) {
      // ILIKE без % — по сути регистронезависимое равенство; TRIM — на случай пробелов в БД/клиенте.
      where += ` AND TRIM(COALESCE(country, '')) ILIKE TRIM($${p}::text)`;
      params.push(String(raw.country).trim());
      p++;
    }

    if (difficultyLevelNum !== undefined && !Number.isNaN(difficultyLevelNum)) {
      where += ` AND difficulty_level = $${p}`;
      params.push(difficultyLevelNum);
      p++;
    }

    if (diveTypesArr.length > 0) {
      where += ` AND site_types && $${p}::varchar[]`;
      params.push(diveTypesArr);
      p++;
    }

    // 0 = «без ограничения» в приложении; иначе COALESCE(depth_max,0) <= 0 отсекает почти всё.
    if (
      minDepth !== undefined &&
      !Number.isNaN(minDepth) &&
      minDepth > 0
    ) {
      where += ` AND COALESCE(depth_max, 0) >= $${p}`;
      params.push(minDepth);
      p++;
    }

    if (
      maxDepth !== undefined &&
      !Number.isNaN(maxDepth) &&
      maxDepth > 0
    ) {
      where += ` AND COALESCE(depth_max, 0) <= $${p}`;
      params.push(maxDepth);
      p++;
    }

    if (
      minRating !== undefined &&
      !Number.isNaN(minRating) &&
      minRating > 0
    ) {
      where += ` AND average_rating >= $${p}`;
      params.push(minRating);
      p++;
    }

    if (q.length > 0) {
      where += ` AND (name ILIKE $${p} OR country ILIKE $${p})`;
      params.push(`%${q.replace(/([%_])/g, '\\$1')}%`);
      p++;
    }

    const countSql = `SELECT COUNT(*)::int AS c FROM dive_sites ${where}`;
    const countRows = await this.dataSource.query(countSql, params);
    const total = countRows?.[0]?.c ?? 0;

    let orderBy = `
      ORDER BY
        (average_rating * LN(COALESCE(review_count, 0) + 1)) DESC,
        review_count DESC,
        id
    `;

    const orderExtra: any[] = [];

    if (sort === 'rating') {
      orderBy = `
        ORDER BY average_rating DESC NULLS LAST, review_count DESC NULLS LAST, id
      `;
    } else if (sort === 'name') {
      orderBy = ` ORDER BY LOWER(name) ASC NULLS LAST, id `;
    } else if (sort === 'reviews') {
      orderBy = ` ORDER BY review_count DESC NULLS LAST, average_rating DESC NULLS LAST, id `;
    } else if (
      sort === 'distance' &&
      userLat !== undefined &&
      !Number.isNaN(userLat) &&
      userLng !== undefined &&
      !Number.isNaN(userLng)
    ) {
      const latIdx = params.length + 1;
      const lngIdx = params.length + 2;
      orderExtra.push(userLat, userLng);
      orderBy = `
        ORDER BY
          (POWER(ST_Y(location::geometry) - $${latIdx}::double precision, 2)
          + POWER(ST_X(location::geometry) - $${lngIdx}::double precision, 2)) ASC NULLS LAST,
          id
      `;
    }

    const limitIdx = params.length + orderExtra.length + 1;
    const offsetIdx = params.length + orderExtra.length + 2;
    const dataParams = [...params, ...orderExtra, limit, offset];

    const dataSql = `
      SELECT 
        id,
        name,
        ST_Y(location::geometry) AS latitude,
        ST_X(location::geometry) AS longitude,
        site_types,
        difficulty_level,
        depth_min,
        depth_max,
        average_rating,
        review_count,
        country,
        region
      FROM dive_sites
      ${where}
      ${orderBy}
      LIMIT $${limitIdx} OFFSET $${offsetIdx}
    `;

    const results = await this.dataSource.query(dataSql, dataParams);

    const data: DiveSiteListItemDto[] = results.map((row: any) => ({
      id: row.id,
      name: row.name,
      latitude: row.latitude ? parseFloat(parseFloat(row.latitude).toFixed(6)) : 0,
      longitude: row.longitude ? parseFloat(parseFloat(row.longitude).toFixed(6)) : 0,
      site_types: Array.isArray(row.site_types) ? row.site_types : [],
      difficulty_level: row.difficulty_level || 1,
      depth_min: row.depth_min ? parseFloat(row.depth_min) : undefined,
      depth_max: row.depth_max ? parseFloat(row.depth_max) : undefined,
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      region: row.region || undefined,
      thumbnail_url: undefined,
    }));

    return { data, total, page, limit };
  }

  /**
   * `listExplore` + iOS-compatible row shape (same as LegacyDiveSitesController.explore).
   */
  async listExploreIosPayload(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    difficultyLevel?: number | string;
    diveTypes?: string | string[];
    minDepth?: number | string;
    maxDepth?: number | string;
    minRating?: number | string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    success: boolean;
    data: any[];
    total: number;
    page: number;
    limit: number;
  }> {
    const result = await this.listExplore(raw);

    const toNum = (v: unknown, fallback = 0): number => {
      const n = Number(v);
      return Number.isFinite(n) ? n : fallback;
    };
    const toStrArr = (v: unknown): string[] => {
      if (Array.isArray(v)) {
        return v.map((x) => String(x));
      }
      if (typeof v === 'string' && v.length > 0) {
        return [v];
      }
      return [];
    };

    const transformed = result.data.map((site) => ({
      id: String((site as { id?: unknown }).id ?? ''),
      name: String((site as { name?: unknown }).name ?? ''),
      description: '',
      latitude: toNum(site.latitude, 0),
      longitude: toNum(site.longitude, 0),
      country: String(site.country ?? ''),
      region: String(site.region ?? ''),
      diveTypes: toStrArr(site.site_types),
      difficultyLevel: Math.min(
        4,
        Math.max(1, Math.floor(toNum(site.difficulty_level, 1))),
      ),
      depthMin: toNum(site.depth_min, 0),
      depthMax: toNum(site.depth_max, 0),
      averageRating: toNum(site.average_rating, 0),
      reviewCount: Math.max(0, Math.floor(toNum(site.review_count, 0))),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }));

    return {
      success: true,
      data: transformed,
      total: result.total,
      page: result.page,
      limit: result.limit,
    };
  }

  /**
   * Get popular dive sites (fallback when no location)
   */
  async getPopular(searchDto: PopularDiveSitesDto): Promise<DiveSiteListItemDto[]> {
    // Omit photo_urls: large JSON per row can blow response size / memory and RST the socket.
    let query = `
      SELECT 
        id,
        name,
        ST_Y(location::geometry) AS latitude,
        ST_X(location::geometry) AS longitude,
        site_types,
        difficulty_level,
        depth_min,
        depth_max,
        average_rating,
        review_count,
        country,
        region
      FROM dive_sites
      WHERE is_active = true
        AND location IS NOT NULL
    `;

    const params: any[] = [];
    let paramIndex = 1;

    if (searchDto.country) {
      query += ` AND country = $${paramIndex}`;
      params.push(searchDto.country);
      paramIndex++;
    }

    query += `
      ORDER BY 
        (average_rating * LN(review_count + 1)) DESC,
        review_count DESC
      LIMIT $${paramIndex}
      OFFSET $${paramIndex + 1}
    `;
    const lim = Math.floor(
      Math.min(500, Math.max(1, Number(searchDto.limit) || 20)),
    );
    const off = Math.floor(Math.max(0, Number(searchDto.offset) || 0));
    params.push(lim, off);

    try {
      const results = await this.dataSource.query(query, params);

      return results.map((row) => ({
      id: row.id,
      name: row.name,
      // Use high precision coordinates (6 decimal places = ~0.1m accuracy)
      latitude: row.latitude ? parseFloat(parseFloat(row.latitude).toFixed(6)) : 0,
      longitude: row.longitude ? parseFloat(parseFloat(row.longitude).toFixed(6)) : 0,
      site_types: Array.isArray(row.site_types) ? row.site_types : [],
      difficulty_level: row.difficulty_level || 1,
      depth_min: row.depth_min ? parseFloat(row.depth_min) : undefined,
      depth_max: row.depth_max ? parseFloat(row.depth_max) : undefined,
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      region: row.region || undefined,
      thumbnail_url: undefined,
      }));
    } catch (error) {
      console.error('Database query error in getPopular:', error);
      console.error('Query:', query);
      console.error('Params:', params);
      throw error;
    }
  }

  /**
   * Get unique countries from dive_sites and dive_centers tables
   */
  async getCountries(): Promise<string[]> {
    const cacheKey = 'countries:all';
    try {
      const cached = await this.cacheManager.get<string[]>(cacheKey);
      if (cached) return cached;
    } catch {}

    const result = await this.dataSource.query(`
      SELECT DISTINCT country
      FROM (
        SELECT country FROM dive_sites WHERE country IS NOT NULL AND country != '' AND is_active = true
        UNION
        SELECT country FROM dive_centers WHERE country IS NOT NULL AND country != ''
      ) combined
      ORDER BY country ASC
    `);

    const countries: string[] = result.map((row: any) => row.country as string);

    try {
      await this.cacheManager.set(cacheKey, countries, 3600000); // 1 hour
    } catch {}

    return countries;
  }

  /**
   * Generate cache key from search parameters
   */
  private generateCacheKey(dto: SearchDiveSitesDto): string {
    const latRounded = Math.round(dto.lat * 1000);
    const lngRounded = Math.round(dto.lng * 1000);
    const filters = JSON.stringify({
      difficulty: dto.difficulty,
      site_types: dto.site_types,
      min_depth: dto.min_depth,
      max_depth: dto.max_depth,
      min_rating: dto.min_rating,
      access_type: dto.access_type,
      country: dto.country,
    });
    const filtersHash = crypto
      .createHash('md5')
      .update(filters)
      .digest('hex')
      .substring(0, 8);

    return `divesites:geo:${latRounded}:${lngRounded}:r${dto.radius || 50000}:f${filtersHash}:sort${dto.sort || 'distance'}:limit${dto.limit || 20}:cursor${dto.cursor || ''}`;
  }

  private generateBoundsCacheKey(dto: MapSearchDto): string {
    const northRounded = Math.round(dto.north * 1000);
    const southRounded = Math.round(dto.south * 1000);
    const eastRounded = Math.round(dto.east * 1000);
    const westRounded = Math.round(dto.west * 1000);
    const filters = JSON.stringify({
      difficulty: dto.difficulty,
      site_types: dto.site_types,
      min_rating: dto.min_rating,
    });
    const filtersHash = crypto
      .createHash('md5')
      .update(filters)
      .digest('hex')
      .substring(0, 8);

    return `divesites:bounds:${northRounded}_${southRounded}_${eastRounded}_${westRounded}:f${filtersHash}`;
  }

  /**
   * Get clusters of dive sites for map display
   */
  async getClusters(
    north: number,
    south: number,
    east: number,
    west: number,
    zoom: number,
    filters?: {
      difficulty?: number;
      site_types?: string[];
      min_rating?: number;
    },
  ): Promise<{
    clusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }>;
    points: DiveSiteListItemDto[];
  }> {
    // Get sites in bounding box
    const sites = await this.searchByBounds({
      north,
      south,
      east,
      west,
      limit: 1000,
      ...filters,
    });

    // Perform clustering
    const { clusters, points } = this.clusterDiveSites(sites, zoom, 0.01);

    return { clusters, points };
  }

  /**
   * Cluster dive sites based on zoom level
   */
  private clusterDiveSites(
    sites: DiveSiteListItemDto[],
    zoom: number,
    distance: number,
  ): {
    clusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }>;
    points: DiveSiteListItemDto[];
  } {
    if (zoom > 15) {
      // High zoom - return all points
      return { clusters: [], points: sites };
    }

    if (zoom < 10) {
      // Low zoom - return only clusters
      const clusters = this.createClusters(sites, distance);
      return { clusters, points: [] };
    }

    // Medium zoom - mixed
    const { clusters, points } = this.createMixedClusters(
      sites,
      distance,
      zoom,
    );
    return { clusters, points };
  }

  /**
   * Create clusters from dive sites
   */
  private createClusters(
    sites: DiveSiteListItemDto[],
    distance: number,
  ): Array<{
    id: string;
    latitude: number;
    longitude: number;
    count: number;
  }> {
    const clusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }> = [];
    const used = new Set<number>();

    for (let i = 0; i < sites.length; i++) {
      if (used.has(i)) {
        continue;
      }

      const site = sites[i];
      const cluster = {
        id: `cluster_${site.id}`,
        latitude: site.latitude,
        longitude: site.longitude,
        count: 1,
      };

      for (let j = i + 1; j < sites.length; j++) {
        if (used.has(j)) {
          continue;
        }

        const dist = this.haversineDistance(
          site.latitude,
          site.longitude,
          sites[j].latitude,
          sites[j].longitude,
        );

        if (dist <= distance) {
          cluster.count++;
          used.add(j);
          // Update cluster center (average)
          cluster.latitude =
            (cluster.latitude * (cluster.count - 1) + sites[j].latitude) /
            cluster.count;
          cluster.longitude =
            (cluster.longitude * (cluster.count - 1) + sites[j].longitude) /
            cluster.count;
        }
      }

      clusters.push(cluster);
      used.add(i);
    }

    return clusters;
  }

  /**
   * Create mixed clusters and points
   */
  private createMixedClusters(
    sites: DiveSiteListItemDto[],
    distance: number,
    zoom: number,
  ): {
    clusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }>;
    points: DiveSiteListItemDto[];
  } {
    const allClusters = this.createClusters(sites, distance);
    const finalClusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }> = [];
    const points: DiveSiteListItemDto[] = [];

    // Separate clusters (count > 1) from single points (count = 1)
    for (const cluster of allClusters) {
      if (cluster.count > 1) {
        finalClusters.push(cluster);
      } else {
        // Find the site for this single-point cluster
        for (const site of sites) {
          const dist = this.haversineDistance(
            site.latitude,
            site.longitude,
            cluster.latitude,
            cluster.longitude,
          );
          if (dist < 0.001) {
            // Very close
            points.push(site);
            break;
          }
        }
      }
    }

    return { clusters: finalClusters, points };
  }

  /**
   * Calculate approximate distance between two points in degrees
   */
  private haversineDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
  ): number {
    // Simplified - returns approximate distance in degrees
    // For production, use proper haversine formula
    const dlat = lat2 - lat1;
    const dlon = lon2 - lon1;
    return Math.sqrt(dlat * dlat + dlon * dlon) * 111.0; // Rough conversion to km
  }

  /**
   * Get cache TTL based on radius
   */
  private getCacheTTL(radius: number): number {
    if (radius < 10000) {
      return 300; // 5 minutes for < 10km
    } else if (radius < 50000) {
      return 900; // 15 minutes for < 50km
    }
    return 3600; // 1 hour for >= 50km
  }
}
