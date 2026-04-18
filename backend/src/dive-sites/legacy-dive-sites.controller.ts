import {
  Controller,
  Get,
  Query,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { DiveSitesService } from './dive-sites.service';
import { DiveSiteListItemDto } from './dto/dive-site-response.dto';

@SkipThrottle()
@Controller('dive-sites')
export class LegacyDiveSitesController {
  constructor(private readonly diveSitesService: DiveSitesService) {}

  /** Nest passes query as string or string[]; `Number([])` / bad shapes must not collapse to page 1 forever. */
  private static parseQueryInt(value: unknown, fallback: number): number {
    const raw = Array.isArray(value) ? value[0] : value;
    if (raw === undefined || raw === null || raw === '') {
      return fallback;
    }
    const n = parseInt(String(raw).trim(), 10);
    return Number.isFinite(n) ? n : fallback;
  }

  /** No DB — e.g. `curl https://api.dive-hub.ru/api/dive-sites/ping` (or local Nest) to verify routing. */
  @Get('ping')
  @HttpCode(HttpStatus.OK)
  ping(): { ok: boolean } {
    return { ok: true };
  }

  /**
   * Paginated explore list (filters + sort + total). iOS Explore (GenericExploreView).
   */
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
  ): Promise<{
    success: boolean;
    data: any[];
    total: number;
    page: number;
    limit: number;
  }> {
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

  // Legacy endpoint for backward compatibility with iOS app
  @Get()
  @HttpCode(HttpStatus.OK)
  async getDiveSitesLegacy(
    @Query('page') page?: number,
    @Query('limit') limit?: number,
    @Query('language') language?: string,
    @Query('diveTypes') diveTypes?: string | string[],
    @Query('difficultyLevel') difficultyLevel?: number | string,
  ): Promise<any[]> {
    // Parse diveTypes - it can come as string, array, or comma-separated string
    let diveTypesArray: string[] = [];
    if (diveTypes) {
      if (Array.isArray(diveTypes)) {
        diveTypesArray = diveTypes;
      } else if (typeof diveTypes === 'string') {
        if (diveTypes.includes(',')) {
          diveTypesArray = diveTypes.split(',').map(s => s.trim());
        } else {
          diveTypesArray = [diveTypes];
        }
      }
    }
    
    // Parse difficultyLevel - it can come as number or string
    let difficultyLevelNum: number | undefined;
    if (difficultyLevel !== undefined && difficultyLevel !== null) {
      if (typeof difficultyLevel === 'string') {
        difficultyLevelNum = parseInt(difficultyLevel, 10);
      } else {
        difficultyLevelNum = difficultyLevel;
      }
    }

    const parsedLimit = LegacyDiveSitesController.parseQueryInt(limit, 50);
    const safeLimit = Math.min(Math.max(parsedLimit, 1), 500);
    const parsedPage = LegacyDiveSitesController.parseQueryInt(page, 1);
    const safePage = Math.max(1, parsedPage);
    const offset = (safePage - 1) * safeLimit;

    try {
      // Use searchByLocation with default location if diveTypes or difficultyLevel filter is provided
      // Otherwise use popular endpoint as fallback for legacy requests
      let sites;
      if (diveTypesArray.length > 0 || difficultyLevelNum !== undefined) {
        // If diveTypes or difficultyLevel filter is provided, use searchByLocation with a global search
        // Use center of world (0,0) with large radius to search globally
        const { SearchDiveSitesDto } = require('./dto/search-dive-sites.dto');
        const searchDto = new SearchDiveSitesDto();
        searchDto.lat = 0;
        searchDto.lng = 0;
        searchDto.radius = 20000000; // Very large radius to cover entire world
        searchDto.limit = Math.min(safeLimit, 100);
        searchDto.sort = 'popularity';
        if (diveTypesArray.length > 0) {
          searchDto.site_types = diveTypesArray; // Map diveTypes to site_types
        }
        if (difficultyLevelNum !== undefined) {
          searchDto.difficulty = difficultyLevelNum; // Map difficultyLevel to difficulty
        }
        const result = await this.diveSitesService.searchByLocation(searchDto);
        sites = result.data || [];
      } else {
        // Use popular endpoint as fallback for legacy requests
        sites = await this.diveSitesService.getPopular({
          limit: safeLimit,
          offset,
        });
      }
      
      // Transform to JSON-safe primitives (pg can return strings/decimals; avoid BigInt / odd types).
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
      const transformed = sites.map((site) => ({
        id: String((site as { id?: unknown }).id ?? ''),
        name: String((site as { name?: unknown }).name ?? ''),
        description: '',
        latitude: toNum(site.latitude, 0),
        longitude: toNum(site.longitude, 0),
        country: String(site.country ?? ''),
        region: String(site.region ?? ''),
        diveTypes: toStrArr(site.site_types),
        difficultyLevel: Math.min(4, Math.max(1, Math.floor(toNum(site.difficulty_level, 1)))),
        depthMin: toNum(site.depth_min, 0),
        depthMax: toNum(site.depth_max, 0),
        averageRating: toNum(site.average_rating, 0),
        reviewCount: Math.max(0, Math.floor(toNum(site.review_count, 0))),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }));

      return transformed;
    } catch (error) {
      console.error('Error in legacy endpoint:', error);
      throw error;
    }
  }
}
