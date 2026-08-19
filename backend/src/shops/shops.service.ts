import { Injectable, Inject, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource } from 'typeorm';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Cache } from 'cache-manager';
import { ShopEntity } from './entities/shop.entity';
import {
  SearchShopsDto,
  MapSearchShopsDto,
  PopularShopsDto,
} from './dto/search-shops.dto';
import { CreateShopDto, UpdateShopDto } from './dto/create-shop.dto';
import { ShopListItemDto, ShopSearchResultDto } from './dto/shop-response.dto';
import * as crypto from 'crypto';

@Injectable()
export class ShopsService {
  constructor(
    @InjectRepository(ShopEntity)
    private shopRepository: Repository<ShopEntity>,
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
    private dataSource: DataSource,
  ) {}

  /**
   * Search shops by location with radius
   */
  async searchByLocation(searchDto: SearchShopsDto): Promise<ShopSearchResultDto> {
    const cacheKey = this.generateCacheKey(searchDto);
    
    try {
      const cached = await this.cacheManager.get<ShopSearchResultDto>(cacheKey);
      if (cached) {
        return cached;
      }
    } catch (error) {
      console.warn('Cache get error:', error.message);
    }

    const radius = searchDto.radius || 50000;
    const limit = searchDto.limit || 20;
    const lat = searchDto.lat;
    const lng = searchDto.lng;
    const place = this.placeFilters(searchDto);
    // Country/region browse should not be limited to the user's nearby radius.
    const restrictByRadius = !place.country && !place.city;

    let query = `
      WITH geo_filtered AS (
        SELECT 
          id,
          name,
          description,
          localized_name,
          localized_description,
          type,
          brands,
          service_available,
          latitude,
          longitude,
          country,
          city,
          address,
          email,
          phone,
          website,
          photo_urls,
          average_rating,
          review_count,
          owner_id,
          created_at,
          updated_at,
          listing_only,
          data_source,
          ST_Distance(
            location::geography,
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
          ) as distance_meters
        FROM shops
        WHERE is_active = true
          AND location IS NOT NULL
    `;

    const params: any[] = [lng, lat, radius];
    let paramIndex = 4;

    if (restrictByRadius) {
      query += `
          AND ST_DWithin(
              location::geography,
              ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
              $3
          )
      `;
    }

    if (searchDto.type) {
      query += ` AND type = $${paramIndex}`;
      params.push(searchDto.type);
      paramIndex++;
    }

    if (searchDto.serviceAvailable !== undefined) {
      query += ` AND service_available = $${paramIndex}`;
      params.push(searchDto.serviceAvailable);
      paramIndex++;
    }

    if (searchDto.search) {
      query += ` AND (
        name ILIKE $${paramIndex} OR 
        description ILIKE $${paramIndex} OR
        array_to_string(brands, ' ') ILIKE $${paramIndex}
      )`;
      params.push(`%${searchDto.search}%`);
      paramIndex++;
    }

    const withPlace = this.appendPlaceSql(query, params, paramIndex, place);
    query = withPlace.query;
    paramIndex = withPlace.paramIndex;

    query += `
      )
      SELECT * FROM geo_filtered
      ORDER BY distance_meters ASC
      LIMIT $${paramIndex}
    `;
    params.push(limit + 1);

    const results = await this.dataSource.query(query, params);

    const shops: ShopListItemDto[] = results.slice(0, limit).map((row) => ({
      id: row.id,
      name: row.name,
      description: row.description || '',
      localizedName: row.localized_name || undefined,
      localizedDescription: row.localized_description || undefined,
      type: row.type,
      brands: Array.isArray(row.brands) ? row.brands : [],
      serviceAvailable: row.service_available || false,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      email: row.email || undefined,
      phone: row.phone || undefined,
      website: row.website || undefined,
      photoUrls: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      averageRating: parseFloat(row.average_rating) || 0,
      reviewCount: row.review_count || 0,
      ownerId: row.owner_id || undefined,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      listingOnly: !!row.listing_only,
      dataSource: row.data_source || undefined,
    }));

    const hasMore = results.length > limit;
    let nextCursor: string | undefined;

    if (hasMore && results.length > limit) {
      const lastRow = results[limit];
      nextCursor = `${lastRow.distance_meters}|${lastRow.id}`;
    }

    const result: ShopSearchResultDto = {
      success: true,
      data: shops,
      cursor: nextCursor,
      total: shops.length,
    };

    try {
      await this.cacheManager.set(cacheKey, result, 300000); // 5 minutes
    } catch (error) {
      console.warn('Cache set error:', error.message);
    }

    return result;
  }

  /**
   * Search shops within bounding box (for map)
   */
  async searchByBounds(searchDto: MapSearchShopsDto): Promise<ShopListItemDto[]> {
    let query = `
      SELECT 
        id,
        name,
        description,
        localized_name,
        localized_description,
        type,
        brands,
        service_available,
        latitude,
        longitude,
        country,
        city,
        address,
        email,
        phone,
        website,
        photo_urls,
        average_rating,
        review_count,
        owner_id,
        created_at,
        updated_at
      FROM shops
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

    if (searchDto.type) {
      query += ` AND type = $${paramIndex}`;
      params.push(searchDto.type);
      paramIndex++;
    }

    const withPlace = this.appendPlaceSql(
      query,
      params,
      paramIndex,
      this.placeFilters(searchDto),
    );
    query = withPlace.query;

    query += ` LIMIT $${withPlace.paramIndex}`;
    params.push(Math.min(500, Math.max(1, searchDto.limit || 500)));

    const results = await this.dataSource.query(query, params);

    return results.map((row) => ({
      id: row.id,
      name: row.name,
      description: row.description || '',
      localizedName: row.localized_name || undefined,
      localizedDescription: row.localized_description || undefined,
      type: row.type,
      brands: Array.isArray(row.brands) ? row.brands : [],
      serviceAvailable: row.service_available || false,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      email: row.email || undefined,
      phone: row.phone || undefined,
      website: row.website || undefined,
      photoUrls: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      averageRating: parseFloat(row.average_rating) || 0,
      reviewCount: row.review_count || 0,
      ownerId: row.owner_id || undefined,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    }));
  }

  /**
   * Get shop by ID
   */
  async findOne(id: string): Promise<ShopListItemDto> {
    const shop = await this.shopRepository.findOne({ where: { id } });
    
    if (!shop) {
      throw new NotFoundException(`Shop with ID ${id} not found`);
    }

    return {
      id: shop.id,
      name: shop.name,
      description: shop.description || '',
      localizedName: shop.localized_name || undefined,
      localizedDescription: shop.localized_description || undefined,
      type: shop.type,
      brands: shop.brands || [],
      serviceAvailable: shop.service_available,
      latitude: shop.latitude || 0,
      longitude: shop.longitude || 0,
      country: shop.country || undefined,
      city: shop.city || undefined,
      address: shop.address || undefined,
      email: shop.email || undefined,
      phone: shop.phone || undefined,
      website: shop.website || undefined,
      photoUrls: shop.photo_urls || [],
      averageRating: parseFloat(shop.average_rating?.toString() || '0'),
      reviewCount: shop.review_count || 0,
      ownerId: shop.owner_id || undefined,
      createdAt: shop.created_at,
      updatedAt: shop.updated_at,
      listingOnly: !!shop.listing_only,
      dataSource: shop.data_source || undefined,
    };
  }

  /**
   * Create a new shop
   */
  async create(createDto: CreateShopDto, ownerId?: string): Promise<ShopEntity> {
    const shop = this.shopRepository.create({
      ...createDto,
      owner_id: ownerId,
      localized_name: createDto.localizedName,
      localized_description: createDto.localizedDescription,
      photo_urls: createDto.photoUrls || [],
      brands: createDto.brands || [],
    });

    return await this.shopRepository.save(shop);
  }

  /**
   * Update a shop
   */
  async update(id: string, updateDto: UpdateShopDto): Promise<ShopEntity> {
    const shop = await this.shopRepository.findOne({ where: { id } });
    
    if (!shop) {
      throw new NotFoundException(`Shop with ID ${id} not found`);
    }

    Object.assign(shop, {
      ...updateDto,
      localized_name: updateDto.localizedName || shop.localized_name,
      localized_description: updateDto.localizedDescription || shop.localized_description,
      photo_urls: updateDto.photoUrls || shop.photo_urls,
      brands: updateDto.brands || shop.brands,
    });

    return await this.shopRepository.save(shop);
  }

  /**
   * Active shops with coordinates for Explore when no geo search params.
   */
  async getPopular(searchDto: PopularShopsDto): Promise<ShopListItemDto[]> {
    const limit = searchDto.limit ?? 20;
    const place = this.placeFilters(searchDto);
    const qb = this.shopRepository
      .createQueryBuilder('s')
      .where('s.is_active = :active', { active: true })
      .andWhere('s.latitude IS NOT NULL')
      .andWhere('s.longitude IS NOT NULL');

    if (place.country) {
      qb.andWhere('LOWER(TRIM(s.country)) = LOWER(TRIM(:country))', {
        country: place.country,
      });
    }
    if (place.city) {
      qb.andWhere('LOWER(TRIM(s.city)) = LOWER(TRIM(:city))', {
        city: place.city,
      });
    }

    const shops = await qb
      .orderBy('s.review_count', 'DESC')
      .addOrderBy('s.average_rating', 'DESC')
      .take(limit)
      .getMany();

    return shops.map((shop) => this.toShopListItemDto(shop));
  }

  /**
   * Paginated explore list with filters + total (same contract as dive-sites/explore).
   * Includes online shops that have no coordinates.
   */
  async listExplore(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    city?: string;
    region?: string;
    type?: string;
    serviceAvailable?: string | boolean;
    minRating?: number | string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    data: ShopListItemDto[];
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

    const place = this.placeFilters({
      country: raw.country,
      city: raw.city,
      region: raw.region,
    });

    let where = ` WHERE is_active = true `;
    const params: any[] = [];
    let p = 1;

    const withPlace = this.appendPlaceSql(where, params, p, place);
    where = withPlace.query;
    p = withPlace.paramIndex;

    const typeRaw = (raw.type || '').trim().toLowerCase();
    if (typeRaw === 'offline' || typeRaw === 'online') {
      where += ` AND type = $${p}`;
      params.push(typeRaw);
      p++;
    }

    const serviceRaw = raw.serviceAvailable;
    if (serviceRaw === true || serviceRaw === 'true' || serviceRaw === '1') {
      where += ` AND service_available = true`;
    } else if (serviceRaw === false || serviceRaw === 'false' || serviceRaw === '0') {
      where += ` AND service_available = false`;
    }

    if (minRating !== undefined && !Number.isNaN(minRating) && minRating > 0) {
      where += ` AND average_rating >= $${p}`;
      params.push(minRating);
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
          OR description ILIKE $${p}
          OR array_to_string(brands, ' ') ILIKE $${p}
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
      `SELECT COUNT(*)::int AS c FROM shops ${where}`,
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
            WHEN type = 'online' THEN 1
            WHEN latitude IS NULL OR longitude IS NULL THEN 1
            WHEN ABS(COALESCE(latitude, 0)) < 0.0001 AND ABS(COALESCE(longitude, 0)) < 0.0001 THEN 1
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
        description,
        localized_name,
        localized_description,
        type,
        brands,
        service_available,
        latitude,
        longitude,
        country,
        city,
        address,
        email,
        phone,
        website,
        photo_urls,
        average_rating,
        review_count,
        owner_id,
        created_at,
        updated_at,
        listing_only,
        data_source
      FROM shops
      ${where}
      ${orderBy}
      LIMIT $${limitIdx} OFFSET $${offsetIdx}
      `,
      dataParams,
    );

    const data: ShopListItemDto[] = results.map((row: any) => ({
      id: row.id,
      name: row.name,
      description: row.description || '',
      localizedName: row.localized_name || undefined,
      localizedDescription: row.localized_description || undefined,
      type: row.type,
      brands: Array.isArray(row.brands) ? row.brands : [],
      serviceAvailable: row.service_available || false,
      latitude: parseFloat(row.latitude) || 0,
      longitude: parseFloat(row.longitude) || 0,
      country: row.country || undefined,
      city: row.city || undefined,
      address: row.address || undefined,
      email: row.email || undefined,
      phone: row.phone || undefined,
      website: row.website || undefined,
      photoUrls: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      averageRating: parseFloat(row.average_rating) || 0,
      reviewCount: row.review_count || 0,
      ownerId: row.owner_id || undefined,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      listingOnly: !!row.listing_only,
      dataSource: row.data_source || undefined,
    }));

    return { data, total, page, limit };
  }

  async listExploreIosPayload(raw: {
    page?: number | string;
    limit?: number | string;
    country?: string;
    city?: string;
    region?: string;
    type?: string;
    serviceAvailable?: string | boolean;
    minRating?: number | string;
    sort?: string;
    userLat?: number | string;
    userLng?: number | string;
    q?: string;
  }): Promise<{
    success: boolean;
    data: ShopListItemDto[];
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

  /** Unique countries that have at least one active shop. */
  async getCountries(): Promise<string[]> {
    const cacheKey = 'countries:shops';
    try {
      const cached = await this.cacheManager.get<string[]>(cacheKey);
      if (cached) return cached;
    } catch {}

    const result = await this.dataSource.query(`
      SELECT DISTINCT country
      FROM shops
      WHERE is_active = true
        AND country IS NOT NULL
        AND TRIM(country) != ''
      ORDER BY country ASC
    `);

    const countries: string[] = result.map((row: { country: string }) =>
      String(row.country),
    );

    try {
      await this.cacheManager.set(cacheKey, countries, 3600000);
    } catch {}

    return countries;
  }

  /** Unique cities (Explore "region") for shops in a country. */
  async getRegions(country: string): Promise<string[]> {
    const trimmed = country?.trim() ?? '';
    if (!trimmed) return [];

    const cacheKey = `regions:shops:${trimmed.toLowerCase()}`;
    try {
      const cached = await this.cacheManager.get<string[]>(cacheKey);
      if (cached) return cached;
    } catch {}

    const result = await this.dataSource.query(
      `
      SELECT DISTINCT city
      FROM shops
      WHERE is_active = true
        AND LOWER(TRIM(country)) = LOWER(TRIM($1))
        AND city IS NOT NULL
        AND TRIM(city) != ''
      ORDER BY city ASC
    `,
      [trimmed],
    );

    const regions: string[] = result.map((row: { city: string }) =>
      String(row.city),
    );

    try {
      await this.cacheManager.set(cacheKey, regions, 3600000);
    } catch {}

    return regions;
  }

  private toShopListItemDto(shop: ShopEntity): ShopListItemDto {
    return {
      id: shop.id,
      name: shop.name,
      description: shop.description || '',
      localizedName: shop.localized_name || undefined,
      localizedDescription: shop.localized_description || undefined,
      type: shop.type,
      brands: shop.brands || [],
      serviceAvailable: shop.service_available,
      latitude: shop.latitude || 0,
      longitude: shop.longitude || 0,
      country: shop.country || undefined,
      city: shop.city || undefined,
      address: shop.address || undefined,
      email: shop.email || undefined,
      phone: shop.phone || undefined,
      website: shop.website || undefined,
      photoUrls: shop.photo_urls || [],
      averageRating: parseFloat(shop.average_rating?.toString() || '0'),
      reviewCount: shop.review_count || 0,
      ownerId: shop.owner_id || undefined,
      createdAt: shop.created_at,
      updatedAt: shop.updated_at,
      listingOnly: !!shop.listing_only,
      dataSource: shop.data_source || undefined,
    };
  }

  /**
   * Get all shops (for admin)
   */
  async findAll(): Promise<ShopListItemDto[]> {
    const shops = await this.shopRepository.find({
      order: { created_at: 'DESC' },
    });

    return shops.map((shop) => this.toShopListItemDto(shop));
  }

  private placeFilters(dto: {
    country?: string;
    city?: string;
    region?: string;
  }): { country?: string; city?: string } {
    const country = dto.country?.trim();
    const city = (dto.city || dto.region)?.trim();
    return {
      country: country || undefined,
      city: city || undefined,
    };
  }

  private appendPlaceSql(
    query: string,
    params: unknown[],
    paramIndex: number,
    place: { country?: string; city?: string },
  ): { query: string; paramIndex: number } {
    if (place.country) {
      query += ` AND LOWER(TRIM(country)) = LOWER(TRIM($${paramIndex}))`;
      params.push(place.country);
      paramIndex++;
    }
    if (place.city) {
      query += ` AND LOWER(TRIM(city)) = LOWER(TRIM($${paramIndex}))`;
      params.push(place.city);
      paramIndex++;
    }
    return { query, paramIndex };
  }

  private generateCacheKey(searchDto: SearchShopsDto): string {
    const place = this.placeFilters(searchDto);
    const keyData = {
      lat: searchDto.lat.toFixed(4),
      lng: searchDto.lng.toFixed(4),
      radius: searchDto.radius,
      limit: searchDto.limit,
      type: searchDto.type,
      serviceAvailable: searchDto.serviceAvailable,
      search: searchDto.search,
      country: place.country,
      city: place.city,
    };
    const keyString = JSON.stringify(keyData);
    return `shops:search:${crypto.createHash('md5').update(keyString).digest('hex')}`;
  }
}
