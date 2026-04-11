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
          ST_Distance(
            location::geography,
            ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
          ) as distance_meters
        FROM shops
        WHERE is_active = true
          AND location IS NOT NULL
          AND ST_DWithin(
              location::geography,
              ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
              $3
          )
    `;

    const params: any[] = [lng, lat, radius];
    let paramIndex = 4;

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

    query += ` LIMIT 500`;

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
    const shops = await this.shopRepository
      .createQueryBuilder('s')
      .where('s.is_active = :active', { active: true })
      .andWhere('s.latitude IS NOT NULL')
      .andWhere('s.longitude IS NOT NULL')
      .orderBy('s.review_count', 'DESC')
      .addOrderBy('s.average_rating', 'DESC')
      .take(limit)
      .getMany();

    return shops.map((shop) => this.toShopListItemDto(shop));
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

  private generateCacheKey(searchDto: SearchShopsDto): string {
    const keyData = {
      lat: searchDto.lat.toFixed(4),
      lng: searchDto.lng.toFixed(4),
      radius: searchDto.radius,
      limit: searchDto.limit,
      type: searchDto.type,
      serviceAvailable: searchDto.serviceAvailable,
      search: searchDto.search,
    };
    const keyString = JSON.stringify(keyData);
    return `shops:search:${crypto.createHash('md5').update(keyString).digest('hex')}`;
  }
}
