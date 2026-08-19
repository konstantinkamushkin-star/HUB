import {
  Injectable,
  Inject,
  BadRequestException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource, In } from 'typeorm';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Cache } from 'cache-manager';
import { DiveCenterEntity } from './entities/dive-center.entity';
import { User } from '../users/entities/user.entity';
import { serializePublicUser } from '../auth/auth.service';
import { PatchDiveCenterInstructorDto } from './dto/patch-dive-center-instructor.dto';
import {
  SearchDiveCentersDto,
  MapSearchCentersDto,
  PopularDiveCentersDto,
} from './dto/search-dive-centers.dto';
import {
  DiveCenterListItemDto,
  DiveCenterSearchResultDto,
} from './dto/dive-center-response.dto';
import * as crypto from 'crypto';

@Injectable()
export class DiveCentersService {
  constructor(
    @InjectRepository(DiveCenterEntity)
    private diveCenterRepository: Repository<DiveCenterEntity>,
    @InjectRepository(User)
    private usersRepository: Repository<User>,
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
    private dataSource: DataSource,
  ) {}

  /**
   * Search dive centers by location with radius
   */
  async searchByLocation(
    searchDto: SearchDiveCentersDto,
  ): Promise<DiveCenterSearchResultDto> {
    const startTime = Date.now();

    // Generate cache key
    const cacheKey = this.generateCacheKey(searchDto);
    
    try {
      const cached = await this.cacheManager.get<DiveCenterSearchResultDto>(
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

    // Build query using raw SQL for better PostGIS support
    const radius = searchDto.radius || 50000;
    const limit = searchDto.limit || 20;
    const lat = searchDto.lat;
    const lng = searchDto.lng;

    let query = `
      WITH geo_filtered AS (
        SELECT 
          id,
          name,
          latitude,
          longitude,
          services,
          average_rating,
          review_count,
          country,
          city,
          address,
          phone,
          email,
          website,
          photo_urls,
          certification_agency,
          nitrox_available,
          price_from,
          listing_only,
          data_source,
          locations,
          ST_Distance(
            location::geography,
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
          ) as distance_meters
        FROM dive_centers
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
    if (searchDto.min_rating !== undefined) {
      query += ` AND average_rating >= $${paramIndex}`;
      params.push(searchDto.min_rating);
      paramIndex++;
    }

    if (searchDto.services && searchDto.services.length > 0) {
      query += ` AND services && $${paramIndex}`;
      params.push(searchDto.services);
      paramIndex++;
    }

    if (searchDto.country) {
      query += ` AND country = $${paramIndex}`;
      params.push(searchDto.country);
      paramIndex++;
    }

    query += `
      )
      SELECT 
        id,
        name,
        latitude,
        longitude,
        services,
        average_rating,
        review_count,
        country,
        city,
        address,
        phone,
        email,
        website,
        photo_urls,
        certification_agency,
        nitrox_available,
        price_from,
        listing_only,
        data_source,
        locations,
        ROUND(distance_meters::numeric, 0)::INTEGER as distance_meters
      FROM geo_filtered
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
      case 'distance':
      default:
        query += ` ORDER BY distance_meters ASC, id`;
        break;
    }

    // Apply cursor AFTER sorting using subquery
    if (cursorId) {
      const sortField = sortBy === 'distance' ? 'distance_meters' : 
                       sortBy === 'rating' ? 'average_rating' : 'review_count';
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
    const centers: DiveCenterListItemDto[] = results.slice(0, limit).map((row) => ({
      id: row.id,
      name: row.name,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      distance_meters: row.distance_meters,
      services: Array.isArray(row.services) ? row.services : [],
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      phone: row.phone || undefined,
      email: row.email || undefined,
      website: row.website || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
      photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      certification_agency: row.certification_agency || undefined,
      nitrox_available: row.nitrox_available || false,
      price_from: row.price_from ? parseFloat(row.price_from) : undefined,
      listing_only: !!row.listing_only,
      data_source: row.data_source || undefined,
      is_partner: !row.listing_only,
      locations: Array.isArray(row.locations) ? row.locations : [],
    }));

    // Check if there's more
    const hasMore = results.length > limit;
    let nextCursor: string | undefined;

    if (hasMore && results.length > limit) {
      const lastRow = results[limit];
      nextCursor = `${lastRow.distance_meters}|${lastRow.id}`;
    }

    const queryTime = Date.now() - startTime;

    const result: DiveCenterSearchResultDto = {
      success: true,
      data: centers,
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
   * Search dive centers within bounding box (for map)
   */
  async searchByBounds(
    searchDto: MapSearchCentersDto,
  ): Promise<DiveCenterListItemDto[]> {
    const cacheKey = this.generateBoundsCacheKey(searchDto);
    
    try {
      const cached = await this.cacheManager.get<DiveCenterListItemDto[]>(
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
        latitude,
        longitude,
        services,
        average_rating,
        review_count,
        country,
        city,
        address,
        phone,
        email,
        website,
        photo_urls,
        certification_agency,
        nitrox_available,
        price_from,
        listing_only,
        data_source,
        locations
      FROM dive_centers
      WHERE is_active = true
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
    if (searchDto.min_rating !== undefined) {
      query += ` AND average_rating >= $${paramIndex}`;
      params.push(searchDto.min_rating);
      paramIndex++;
    }

    if (searchDto.services && searchDto.services.length > 0) {
      query += ` AND services && $${paramIndex}`;
      params.push(searchDto.services);
      paramIndex++;
    }

    if (searchDto.country && String(searchDto.country).trim()) {
      query += ` AND TRIM(COALESCE(country, '')) ILIKE TRIM($${paramIndex}::text)`;
      params.push(String(searchDto.country).trim());
      paramIndex++;
    }

    query += ` LIMIT $${paramIndex}`;
    params.push(searchDto.limit || 500);

    const results = await this.dataSource.query(query, params);

    const centers: DiveCenterListItemDto[] = results.map((row) => ({
      id: row.id,
      name: row.name,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      services: Array.isArray(row.services) ? row.services : [],
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      phone: row.phone || undefined,
      email: row.email || undefined,
      website: row.website || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
      photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      certification_agency: row.certification_agency || undefined,
      nitrox_available: row.nitrox_available || false,
      price_from: row.price_from ? parseFloat(row.price_from) : undefined,
      listing_only: !!row.listing_only,
      data_source: row.data_source || undefined,
      is_partner: !row.listing_only,
      locations: Array.isArray(row.locations) ? row.locations : [],
    }));

    // Cache result
    try {
      const ttl = this.getCacheTTL(50000);
      await this.cacheManager.set(cacheKey, centers, ttl);
    } catch (error) {
      console.warn('Cache set error:', error.message);
    }

    return centers;
  }

  /**
   * Get popular dive centers (fallback when no location)
   */
    async getPopular(searchDto: PopularDiveCentersDto): Promise<DiveCenterListItemDto[]> {
    let query = `
      SELECT 
        id,
        name,
        latitude,
        longitude,
        services,
        average_rating,
        review_count,
        country,
        city,
        address,
        phone,
        email,
        website,
        photo_urls,
        certification_agency,
        nitrox_available,
        price_from,
        listing_only,
        data_source,
        locations
      FROM dive_centers
      WHERE is_active = true
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
        CASE WHEN listing_only IS TRUE THEN 1 ELSE 0 END ASC,
        (average_rating * LN(review_count + 1)) DESC,
        review_count DESC
      LIMIT $${paramIndex}
    `;
    params.push(searchDto.limit || 20);

    try {
      const results = await this.dataSource.query(query, params);

      return results.map((row) => ({
        id: row.id,
        name: row.name,
        latitude: parseFloat(row.latitude) || 0,
        longitude: parseFloat(row.longitude) || 0,
        services: Array.isArray(row.services) ? row.services : [],
        average_rating: parseFloat(row.average_rating) || 0,
        review_count: row.review_count || 0,
        country: row.country || undefined,
        city: row.city || undefined,
        address: row.address || undefined,
        phone: row.phone || undefined,
        email: row.email || undefined,
        website: row.website || undefined,
        thumbnail_url:
          Array.isArray(row.photo_urls) && row.photo_urls.length > 0
            ? row.photo_urls[0]
            : undefined,
        photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
        certification_agency: row.certification_agency || undefined,
        nitrox_available: row.nitrox_available || false,
        price_from: row.price_from ? parseFloat(row.price_from) : undefined,
        listing_only: !!row.listing_only,
        data_source: row.data_source || undefined,
        is_partner: !row.listing_only,
        locations: Array.isArray(row.locations) ? row.locations : [],
      }));
    } catch (error) {
      console.error('Database query error in getPopular:', error);
      console.error('Query:', query);
      console.error('Params:', params);
      throw error;
    }
  }

  /**
   * Paginated explore list with filters + total (same contract as dive-sites/explore).
   */
  async listExplore(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    city?: string;
    minRating?: number | string;
    certificationAgency?: string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    data: DiveCenterListItemDto[];
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
    `;
    const params: any[] = [];
    let p = 1;

    if (raw.country && String(raw.country).trim()) {
      where += ` AND TRIM(COALESCE(country, '')) ILIKE TRIM($${p}::text)`;
      params.push(String(raw.country).trim());
      p++;
    }
    if (raw.city && String(raw.city).trim()) {
      where += ` AND TRIM(COALESCE(city, '')) ILIKE TRIM($${p}::text)`;
      params.push(String(raw.city).trim());
      p++;
    }
    if (minRating !== undefined && !Number.isNaN(minRating) && minRating > 0) {
      where += ` AND average_rating >= $${p}`;
      params.push(minRating);
      p++;
    }
    if (raw.certificationAgency && String(raw.certificationAgency).trim()) {
      where += ` AND COALESCE(certification_agency, '') ILIKE $${p}`;
      params.push(`%${String(raw.certificationAgency).trim()}%`);
      p++;
    }

    if (q.length > 0) {
      const escaped = q.replace(/([%_])/g, '\\$1');
      if (q.length < 3) {
        where += ` AND (
          name ILIKE $${p}
          OR name ILIKE $${p + 1}
        )`;
        params.push(`${escaped}%`);
        params.push(`% ${escaped}%`);
        p += 2;
      } else {
        where += ` AND (
          name ILIKE $${p}
          OR TRIM(COALESCE(city, '')) ILIKE $${p + 1}
          OR TRIM(COALESCE(country, '')) ILIKE $${p + 2}
          OR TRIM(COALESCE(country, '')) ILIKE TRIM($${p + 3}::text)
        )`;
        params.push(`%${escaped}%`);
        params.push(`${escaped}%`);
        params.push(`${escaped}%`);
        params.push(q);
        p += 4;
      }
    }

    const countRows = await this.dataSource.query(
      `SELECT COUNT(*)::int AS c FROM dive_centers ${where}`,
      params,
    );
    const total = countRows?.[0]?.c ?? 0;

    let orderBy = `
      ORDER BY
        CASE WHEN listing_only IS TRUE THEN 1 ELSE 0 END ASC,
        (average_rating * LN(COALESCE(review_count, 0) + 1)) DESC,
        review_count DESC,
        id
    `;
    const orderExtra: any[] = [];

    if (sort === 'rating') {
      orderBy = ` ORDER BY average_rating DESC NULLS LAST, review_count DESC NULLS LAST, id `;
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
          CASE
            WHEN latitude IS NULL OR longitude IS NULL THEN 1
            WHEN ABS(latitude) < 0.0001 AND ABS(longitude) < 0.0001 THEN 1
            ELSE 0
          END ASC,
          (POWER(COALESCE(latitude, 0) - $${latIdx}::double precision, 2)
          + POWER(COALESCE(longitude, 0) - $${lngIdx}::double precision, 2)) ASC NULLS LAST,
          id
      `;
    }

    const limitIdx = params.length + orderExtra.length + 1;
    const offsetIdx = params.length + orderExtra.length + 2;
    const dataParams = [...params, ...orderExtra, limit, offset];

    const results = await this.dataSource.query(
      `
      SELECT
        id,
        name,
        latitude,
        longitude,
        services,
        average_rating,
        review_count,
        country,
        city,
        address,
        phone,
        email,
        website,
        photo_urls,
        certification_agency,
        nitrox_available,
        price_from,
        listing_only,
        data_source,
        locations
      FROM dive_centers
      ${where}
      ${orderBy}
      LIMIT $${limitIdx} OFFSET $${offsetIdx}
      `,
      dataParams,
    );

    const data: DiveCenterListItemDto[] = results.map((row: any) => ({
      id: row.id,
      name: row.name,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      services: Array.isArray(row.services) ? row.services : [],
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      phone: row.phone || undefined,
      email: row.email || undefined,
      website: row.website || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
      photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      certification_agency: row.certification_agency || undefined,
      nitrox_available: row.nitrox_available || false,
      price_from: row.price_from ? parseFloat(row.price_from) : undefined,
      listing_only: !!row.listing_only,
      data_source: row.data_source || undefined,
      is_partner: !row.listing_only,
      locations: Array.isArray(row.locations) ? row.locations : [],
    }));

    return { data, total, page, limit };
  }

  async listExploreIosPayload(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    city?: string;
    minRating?: number | string;
    certificationAgency?: string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    success: boolean;
    data: DiveCenterListItemDto[];
    total: number;
    page: number;
    limit: number;
  }> {
    const result = await this.listExplore(raw);
    return {
      success: true,
      data: result.data,
      total: result.total,
      page: result.page,
      limit: result.limit,
    };
  }

  /** Single public dive center (mobile detail / iOS `getDiveCenter`). */
  async getPublicById(id: string): Promise<DiveCenterListItemDto> {
    const results = await this.dataSource.query(
      `
      SELECT
        id,
        name,
        latitude,
        longitude,
        services,
        average_rating,
        review_count,
        country,
        city,
        address,
        phone,
        email,
        website,
        photo_urls,
        certification_agency,
        nitrox_available,
        price_from,
        description,
        listing_only,
        data_source,
        locations
      FROM dive_centers
      WHERE id = $1 AND is_active = true
      LIMIT 1
    `,
      [id],
    );
    if (!results?.length) {
      throw new NotFoundException('Dive center not found');
    }
    const row = results[0];
    const listingOnly = !!row.listing_only;
    return {
      id: row.id,
      name: row.name,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      services: Array.isArray(row.services) ? row.services : [],
      average_rating: parseFloat(row.average_rating) || 0,
      review_count: row.review_count || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      phone: row.phone || undefined,
      email: row.email || undefined,
      website: row.website || undefined,
      thumbnail_url:
        Array.isArray(row.photo_urls) && row.photo_urls.length > 0
          ? row.photo_urls[0]
          : undefined,
      photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      certification_agency: row.certification_agency || undefined,
      nitrox_available: row.nitrox_available || false,
      price_from: row.price_from ? parseFloat(row.price_from) : undefined,
      description: row.description || undefined,
      listing_only: listingOnly,
      data_source: row.data_source || undefined,
      is_partner: !listingOnly,
      locations: Array.isArray(row.locations) ? row.locations : [],
    };
  }

  /** Unique countries that have at least one active dive center. */
  async getCountries(): Promise<string[]> {
    const cacheKey = 'countries:divecenters';
    try {
      const cached = await this.cacheManager.get<string[]>(cacheKey);
      if (cached) return cached;
    } catch {}

    const result = await this.dataSource.query(`
      SELECT DISTINCT country
      FROM dive_centers
      WHERE is_active = true
        AND country IS NOT NULL
        AND TRIM(country) != ''
      ORDER BY country ASC
    `);

    const countries: string[] = result.map((row: any) => String(row.country));

    try {
      await this.cacheManager.set(cacheKey, countries, 3600000);
    } catch {}

    return countries;
  }

  /**
   * Generate cache key from search parameters
   */
  private generateCacheKey(dto: SearchDiveCentersDto): string {
    const latRounded = Math.round(dto.lat * 1000);
    const lngRounded = Math.round(dto.lng * 1000);
    const filters = JSON.stringify({
      min_rating: dto.min_rating,
      services: dto.services,
      country: dto.country,
    });
    const filtersHash = crypto
      .createHash('md5')
      .update(filters)
      .digest('hex')
      .substring(0, 8);

    return `divecenters:geo:${latRounded}:${lngRounded}:r${dto.radius || 50000}:f${filtersHash}:sort${dto.sort || 'distance'}:limit${dto.limit || 20}:cursor${dto.cursor || ''}`;
  }

    private generateBoundsCacheKey(dto: MapSearchCentersDto): string {
    const northRounded = Math.round(dto.north * 1000);
    const southRounded = Math.round(dto.south * 1000);
    const eastRounded = Math.round(dto.east * 1000);
    const westRounded = Math.round(dto.west * 1000);
    const filters = JSON.stringify({
      min_rating: dto.min_rating,
      services: dto.services,
      country: dto.country,
    });
    const filtersHash = crypto
      .createHash('md5')
      .update(filters)
      .digest('hex')
      .substring(0, 8);

    return `divecenters:bounds:${northRounded}_${southRounded}_${eastRounded}_${westRounded}:f${filtersHash}`;
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

  /**
   * Get instructors for a dive center
   */
  async getInstructors(diveCenterId: string): Promise<any[]> {
    const center = await this.diveCenterRepository.findOne({
      where: { id: diveCenterId },
    });
    if (!center) {
      return [];
    }
    const ids = center.instructor_ids ?? [];
    if (ids.length === 0) {
      return [];
    }
    const users = await this.usersRepository.findBy({ id: In(ids) });
    return users.map((u) => this.formatInstructorPublic(u, diveCenterId));
  }

  /** Список пользователей-инструкторов для панели центра (мобильное приложение). */
  async listInstructorUsersForCenterAdmin(
    diveCenterId: string,
    actorUserId: string,
  ): Promise<ReturnType<typeof serializePublicUser>[]> {
    const center = await this.diveCenterRepository.findOne({
      where: { id: diveCenterId },
    });
    if (!center) {
      throw new NotFoundException('Dive center not found');
    }
    await this.assertUserCanManageInstructors(center, actorUserId);
    const ids = center.instructor_ids ?? [];
    if (ids.length === 0) {
      return [];
    }
    const users = await this.usersRepository.findBy({ id: In(ids) });
    return users.map((u) => serializePublicUser(u));
  }

  private formatInstructorPublic(user: User, diveCenterId: string): Record<string, unknown> {
    const name =
      `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email;
    return {
      id: user.id,
      userId: user.id,
      name,
      avatarURL: user.avatarUrl ?? null,
      photoURL: user.avatarUrl ?? null,
      bio: user.bio ?? null,
      certifications: [],
      languages: [],
      trainingSystems: [],
      credentials: [],
      averageRating: 0,
      reviewCount: 0,
      diveCenterId,
    };
  }

  private async assertUserCanManageInstructors(
    center: DiveCenterEntity,
    actorUserId: string,
  ): Promise<void> {
    if (center.owner_id === actorUserId) {
      return;
    }

    const actor = await this.usersRepository.findOne({
      where: { id: actorUserId },
    });
    if (!actor) {
      throw new ForbiddenException('Нет прав');
    }
    if (actor.role === 'SUPER_ADMIN') {
      return;
    }

    const role = (actor.role ?? '').toUpperCase();
    if (role === 'DIVE_CENTER_ADMIN' || role === 'INSTRUCTOR') {
      if ((center.instructor_ids ?? []).includes(actorUserId)) {
        return;
      }
      const a = actor.email?.toLowerCase().trim();
      const c = center.email?.toLowerCase().trim();
      if (a && c && a === c) {
        return;
      }
    }

    throw new ForbiddenException(
      'Нет прав на управление инструкторами этого центра',
    );
  }

  async addInstructorMember(
    diveCenterId: string,
    userIdToAdd: string,
    actorUserId: string,
  ): Promise<Record<string, unknown>> {
    const center = await this.diveCenterRepository.findOne({
      where: { id: diveCenterId },
    });
    if (!center) {
      throw new NotFoundException('Dive center not found');
    }
    await this.assertUserCanManageInstructors(center, actorUserId);

    const target = await this.usersRepository.findOne({
      where: { id: userIdToAdd },
    });
    if (!target) {
      throw new NotFoundException('Пользователь не найден');
    }

    const ids = [...(center.instructor_ids ?? [])];
    if (!ids.includes(userIdToAdd)) {
      ids.push(userIdToAdd);
      center.instructor_ids = ids;
      await this.diveCenterRepository.save(center);
    }

    const r = (target.role ?? '').toUpperCase();
    if (r === 'DIVER_BASIC') {
      target.role = 'INSTRUCTOR';
      await this.usersRepository.save(target);
    }

    return this.formatInstructorPublic(target, diveCenterId);
  }

  async removeInstructorMember(
    diveCenterId: string,
    memberUserId: string,
    actorUserId: string,
  ): Promise<void> {
    const center = await this.diveCenterRepository.findOne({
      where: { id: diveCenterId },
    });
    if (!center) {
      throw new NotFoundException('Dive center not found');
    }
    await this.assertUserCanManageInstructors(center, actorUserId);

    const ids = (center.instructor_ids ?? []).filter((x) => x !== memberUserId);
    center.instructor_ids = ids;
    await this.diveCenterRepository.save(center);
  }

  async updateInstructorProfile(
    diveCenterId: string,
    targetUserId: string,
    actorUserId: string,
    dto: PatchDiveCenterInstructorDto,
  ): Promise<void> {
    const center = await this.diveCenterRepository.findOne({
      where: { id: diveCenterId },
    });
    if (!center) {
      throw new NotFoundException('Dive center not found');
    }
    await this.assertUserCanManageInstructors(center, actorUserId);
    const ids = center.instructor_ids ?? [];
    if (!ids.includes(targetUserId)) {
      throw new BadRequestException(
        'Пользователь не в списке инструкторов этого центра',
      );
    }
    const target = await this.usersRepository.findOne({
      where: { id: targetUserId },
    });
    if (!target) {
      throw new NotFoundException('Пользователь не найден');
    }
    if (dto.bio !== undefined) {
      const t = dto.bio?.trim();
      target.bio = t && t.length > 0 ? t : null;
    }
    await this.usersRepository.save(target);
  }

  /** Центры, для которых пользователь может создавать поездки (как в TripsWriteService.assertUserCanImportTripsForDiveCenter). */
  async listCentersManagedForTripCreation(
    userId: string,
    userRole?: string,
  ): Promise<{ id: string; name: string }[]> {
    const role = (userRole ?? '').toUpperCase();
    if (role === 'SUPER_ADMIN') {
      const rows = await this.dataSource.query(
        `SELECT id, name FROM dive_centers WHERE deleted_at IS NULL ORDER BY name ASC LIMIT 200`,
      );
      return rows.map((r: { id: string; name: string }) => ({
        id: r.id,
        name: r.name,
      }));
    }
    const user = await this.usersRepository.findOne({ where: { id: userId } });
    if (!user) {
      return [];
    }
    const map = new Map<string, string>();

    const owners = await this.dataSource.query(
      `SELECT id, name FROM dive_centers WHERE owner_id = $1 AND deleted_at IS NULL`,
      [userId],
    );
    for (const r of owners) {
      map.set(r.id, r.name);
    }

    const instructors = await this.dataSource.query(
      `SELECT id, name FROM dive_centers WHERE deleted_at IS NULL AND $1 = ANY(instructor_ids)`,
      [userId],
    );
    for (const r of instructors) {
      map.set(r.id, r.name);
    }

    if (role === 'DIVE_CENTER_ADMIN' && user.email) {
      const email = user.email.toLowerCase().trim();
      const byEmail = await this.dataSource.query(
        `SELECT id, name FROM dive_centers WHERE deleted_at IS NULL AND LOWER(TRIM(email)) = $1`,
        [email],
      );
      for (const r of byEmail) {
        map.set(r.id, r.name);
      }
    }

    return [...map.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async getAffiliatedSitesForCenterAdmin(
    centerId: string,
    actorId: string,
    actorRole?: string,
  ): Promise<{ siteIds: string[] }> {
    await this.assertCanManageCenterForMobileAdmin(actorId, actorRole, centerId);
    const rows = await this.dataSource.query(
      `SELECT affiliated_sites FROM dive_centers WHERE id = $1::uuid AND deleted_at IS NULL LIMIT 1`,
      [centerId],
    );
    if (!rows.length) {
      throw new NotFoundException('Dive center not found');
    }
    const raw = rows[0].affiliated_sites;
    const ids = Array.isArray(raw) ? raw.map((x: unknown) => String(x)) : [];
    return { siteIds: ids };
  }

  async setAffiliatedSitesForCenterAdmin(
    centerId: string,
    actorId: string,
    actorRole: string | undefined,
    siteIds: string[],
  ): Promise<{ siteIds: string[] }> {
    await this.assertCanManageCenterForMobileAdmin(actorId, actorRole, centerId);
    const uniq = [
      ...new Set(
        siteIds.map((s) => String(s).trim()).filter((s) => s.length > 0),
      ),
    ];
    if (uniq.length) {
      const found = await this.dataSource.query(
        `SELECT id::text AS id FROM dive_sites WHERE id = ANY($1::uuid[]) AND deleted_at IS NULL`,
        [uniq],
      );
      const ok = new Set(
        found.map((r: { id: string }) => String(r.id).toLowerCase()),
      );
      for (const id of uniq) {
        if (!ok.has(id.toLowerCase())) {
          throw new BadRequestException(
            `Unknown or deleted dive site: ${id}`,
          );
        }
      }
    }
    await this.dataSource.query(
      `UPDATE dive_centers SET affiliated_sites = $2::uuid[], updated_at = NOW() WHERE id = $1::uuid AND deleted_at IS NULL`,
      [centerId, uniq],
    );
    return { siteIds: uniq };
  }

  /** Same rules as TripsWriteService.assertUserCanImportTripsForDiveCenter (mobile JWT). */
  async assertCanManageCenterForMobileAdmin(
    actorId: string,
    actorRole: string | undefined,
    centerId: string,
  ): Promise<void> {
    const roleUpper = (actorRole ?? '').toUpperCase();
    if (roleUpper === 'SUPER_ADMIN') {
      const r = await this.dataSource.query(
        `SELECT id FROM dive_centers WHERE id = $1::uuid AND deleted_at IS NULL LIMIT 1`,
        [centerId],
      );
      if (!r.length) {
        throw new NotFoundException('Dive center not found');
      }
      return;
    }
    const r = await this.dataSource.query(
      `SELECT id, owner_id, email, instructor_ids FROM dive_centers WHERE id = $1::uuid AND deleted_at IS NULL LIMIT 1`,
      [centerId],
    );
    if (!r?.length) {
      throw new NotFoundException('Dive center not found');
    }
    const row = r[0] as {
      owner_id?: string | null;
      email?: string | null;
      instructor_ids?: string[] | null;
    };
    if (row.owner_id === actorId) {
      return;
    }
    const instructors = Array.isArray(row.instructor_ids)
      ? row.instructor_ids
      : [];
    if (instructors.includes(actorId)) {
      return;
    }
    if (roleUpper === 'DIVE_CENTER_ADMIN') {
      const uRows = await this.dataSource.query(
        `SELECT email FROM users WHERE id = $1 LIMIT 1`,
        [actorId],
      );
      const uEmail = String(uRows[0]?.email ?? '')
        .toLowerCase()
        .trim();
      const cEmail = String(row.email ?? '')
        .toLowerCase()
        .trim();
      if (uEmail && cEmail && uEmail === cEmail) {
        return;
      }
    }
    throw new ForbiddenException(
      'No permission to manage affiliated sites for this dive center',
    );
  }
}
