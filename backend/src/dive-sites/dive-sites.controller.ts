import {
  Controller,
  Get,
  Query,
  HttpCode,
  HttpStatus,
  ParseArrayPipe,
  UsePipes,
  ValidationPipe,
} from '@nestjs/common';
import { DiveSitesService } from './dive-sites.service';
import {
  SearchDiveSitesDto,
  MapSearchDto,
  PopularDiveSitesDto,
  ClustersDto,
} from './dto/search-dive-sites.dto';
import {
  DiveSiteSearchResultDto,
  DiveSiteListItemDto,
} from './dto/dive-site-response.dto';
import { NoValidationPipe } from '../common/pipes/no-validation.pipe';

@Controller('v1/dive-sites')
export class DiveSitesController {
  constructor(private readonly diveSitesService: DiveSitesService) {}

  @Get('test')
  test() {
    return { message: 'Controller is working!' };
  }

  @Get('countries')
  @HttpCode(HttpStatus.OK)
  async getCountries(): Promise<{ success: boolean; data: string[] }> {
    const countries = await this.diveSitesService.getCountries();
    return { success: true, data: countries };
  }

  /** Paginated explore (same body as `GET /api/dive-sites/explore`). Prefer this path on clients. */
  @Get('explore')
  @HttpCode(HttpStatus.OK)
  async explore(
    @Query('page') page?: number,
    @Query('limit') limit?: number,
    @Query('language') language?: string,
    @Query('country') country?: string,
    @Query('difficultyLevel') difficultyLevel?: number,
    @Query('diveTypes') diveTypes?: string | string[],
    @Query('minDepth') minDepth?: number,
    @Query('maxDepth') maxDepth?: number,
    @Query('minRating') minRating?: number,
    @Query('sort') sort?: string,
    @Query('userLat') userLat?: number,
    @Query('userLng') userLng?: number,
    @Query('q') q?: string,
  ) {
    return this.diveSitesService.listExploreIosPayload({
      page,
      limit,
      country,
      difficultyLevel,
      diveTypes,
      minDepth,
      maxDepth,
      minRating,
      sort,
      userLat,
      userLng,
      q,
    });
  }

  // Legacy endpoint for backward compatibility
  @Get()
  @HttpCode(HttpStatus.OK)
  async getDiveSitesLegacy(
    @Query('page') page?: number,
    @Query('limit') limit?: number,
    @Query('language') language?: string,
  ): Promise<{
    success: boolean;
    data: DiveSiteListItemDto[];
    pagination?: any;
  }> {
    try {
      const parsedLimit = limit !== undefined && limit !== null ? Number(limit) : 50;
      const safeLimit = Math.min(
        Math.max(Number.isFinite(parsedLimit) ? parsedLimit : 50, 1),
        500,
      );
      const parsedPage = page !== undefined && page !== null ? Number(page) : 1;
      const safePage = Math.max(
        1,
        Number.isFinite(parsedPage) ? parsedPage : 1,
      );
      const offset = (safePage - 1) * safeLimit;
      const sites = await this.diveSitesService.getPopular({
        limit: safeLimit,
        offset,
      });
      
      return {
        success: true,
        data: sites,
      };
    } catch (error) {
      console.error('Error in legacy endpoint:', error);
      throw error;
    }
  }

  @Get('search')
  @HttpCode(HttpStatus.OK)
  @UsePipes(new NoValidationPipe()) // Disable global ValidationPipe for this endpoint
  async search(
    @Query() rawQuery: any,
  ): Promise<DiveSiteSearchResultDto> {
    // Transform site_types from string to array if needed
    if (rawQuery.site_types !== undefined && rawQuery.site_types !== null) {
      if (!Array.isArray(rawQuery.site_types)) {
        rawQuery.site_types = [rawQuery.site_types];
      }
    }
    
    // Manually validate and transform the DTO (bypassing ValidationPipe for site_types)
    const searchDto = new SearchDiveSitesDto();
    searchDto.lat = parseFloat(rawQuery.lat) || 0;
    searchDto.lng = parseFloat(rawQuery.lng) || 0;
    searchDto.radius = rawQuery.radius ? parseFloat(rawQuery.radius) : 50000;
    searchDto.limit = rawQuery.limit ? parseInt(rawQuery.limit) : 20;
    searchDto.sort = rawQuery.sort || 'distance';
    searchDto.cursor = rawQuery.cursor;
    searchDto.difficulty = rawQuery.difficulty ? parseInt(rawQuery.difficulty) : undefined;
    searchDto.site_types = rawQuery.site_types; // Already transformed above
    searchDto.min_depth = rawQuery.min_depth ? parseFloat(rawQuery.min_depth) : undefined;
    searchDto.max_depth = rawQuery.max_depth ? parseFloat(rawQuery.max_depth) : undefined;
    searchDto.min_rating = rawQuery.min_rating ? parseFloat(rawQuery.min_rating) : undefined;
    searchDto.access_type = rawQuery.access_type ? (Array.isArray(rawQuery.access_type) ? rawQuery.access_type : [rawQuery.access_type]) : undefined;
    searchDto.country = rawQuery.country;
    
    try {
      const result = await this.diveSitesService.searchByLocation(searchDto);
      return result;
    } catch (error) {
      console.error('Error in search endpoint:', error);
      throw error;
    }
  }

  @Get('map')
  @HttpCode(HttpStatus.OK)
  async mapSearch(@Query() rawQuery: any): Promise<{
    success: boolean;
    data: DiveSiteListItemDto[];
  }> {
    // Transform site_types from string to array if needed
    if (rawQuery.site_types !== undefined && rawQuery.site_types !== null) {
      if (!Array.isArray(rawQuery.site_types)) {
        rawQuery.site_types = [rawQuery.site_types];
      }
    }
    
    const searchDto: MapSearchDto = rawQuery as MapSearchDto;
    
    try {
      const sites = await this.diveSitesService.searchByBounds(searchDto);
      return {
        success: true,
        data: sites,
      };
    } catch (error) {
      console.error('Error in map endpoint:', error);
      throw error;
    }
  }

  @Get('popular')
  @HttpCode(HttpStatus.OK)
  async popular(@Query() searchDto: PopularDiveSitesDto): Promise<{
    success: boolean;
    data: DiveSiteListItemDto[];
  }> {
    try {
      const sites = await this.diveSitesService.getPopular(searchDto);
      return {
        success: true,
        data: sites,
      };
    } catch (error) {
      console.error('Error in popular endpoint:', error);
      throw error;
    }
  }

  @Get('clusters')
  @HttpCode(HttpStatus.OK)
  async clusters(@Query() clustersDto: ClustersDto): Promise<{
    success: boolean;
    clusters: Array<{
      id: string;
      latitude: number;
      longitude: number;
      count: number;
    }>;
    points: DiveSiteListItemDto[];
  }> {
    try {
      const filters: {
        difficulty?: number;
        site_types?: string[];
        min_rating?: number;
      } = {};

      if (clustersDto.difficulty) {
        filters.difficulty = clustersDto.difficulty;
      }

      if (clustersDto.site_types) {
        filters.site_types = clustersDto.site_types.split(',');
      }

      if (clustersDto.min_rating !== undefined) {
        filters.min_rating = clustersDto.min_rating;
      }

      const result = await this.diveSitesService.getClusters(
        clustersDto.north,
        clustersDto.south,
        clustersDto.east,
        clustersDto.west,
        clustersDto.zoom || 10,
        filters,
      );

      return {
        success: true,
        ...result,
      };
    } catch (error) {
      console.error('Error in clusters endpoint:', error);
      throw error;
    }
  }
}
