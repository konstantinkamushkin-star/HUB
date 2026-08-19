import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  ParseUUIDPipe,
  Query,
  HttpCode,
  HttpStatus,
  UseGuards,
  Request,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { ShopsService } from './shops.service';
import { ShopCommerceService } from './shop-commerce.service';
import {
  SearchShopsDto,
  MapSearchShopsDto,
  PopularShopsDto,
} from './dto/search-shops.dto';
import { CreateShopDto, UpdateShopDto } from './dto/create-shop.dto';
import { ShopSearchResultDto, ShopListItemDto } from './dto/shop-response.dto';

@Controller('v1/shops')
export class ShopsController {
  constructor(
    private readonly shopsService: ShopsService,
    private readonly shopCommerce: ShopCommerceService,
  ) {
    console.log('✅ ShopsController initialized');
  }

  @Get('search')
  @HttpCode(HttpStatus.OK)
  async search(@Query() searchDto: SearchShopsDto): Promise<ShopSearchResultDto> {
    try {
      return await this.shopsService.searchByLocation(searchDto);
    } catch (error) {
      console.error('Error in search endpoint:', error);
      throw error;
    }
  }

  @Get('popular')
  @HttpCode(HttpStatus.OK)
  async popular(@Query() searchDto: PopularShopsDto): Promise<{
    success: boolean;
    data: ShopListItemDto[];
  }> {
    const data = await this.shopsService.getPopular(searchDto);
    return { success: true, data };
  }

  /** Paginated explore (same contract as `GET /v1/dive-sites/explore`). */
  @Get('explore')
  @HttpCode(HttpStatus.OK)
  async explore(
    @Query('page') page?: number,
    @Query('limit') limit?: number,
    @Query('country') country?: string,
    @Query('city') city?: string,
    @Query('region') region?: string,
    @Query('type') type?: string,
    @Query('serviceAvailable') serviceAvailable?: string,
    @Query('minRating') minRating?: number,
    @Query('sort') sort?: string,
    @Query('userLat') userLat?: number,
    @Query('userLng') userLng?: number,
    @Query('q') q?: string,
  ) {
    return this.shopsService.listExploreIosPayload({
      page,
      limit,
      country,
      city,
      region,
      type,
      serviceAvailable,
      minRating,
      sort,
      userLat,
      userLng,
      q,
    });
  }

  @Get('countries')
  @HttpCode(HttpStatus.OK)
  async getCountries(): Promise<{ success: boolean; data: string[] }> {
    const countries = await this.shopsService.getCountries();
    return { success: true, data: countries };
  }

  @Get('regions')
  @HttpCode(HttpStatus.OK)
  async getRegions(
    @Query('country') country?: string,
  ): Promise<{ success: boolean; data: string[] }> {
    const regions = await this.shopsService.getRegions(country ?? '');
    return { success: true, data: regions };
  }

  @Get('map')
  @HttpCode(HttpStatus.OK)
  async mapSearch(@Query() searchDto: MapSearchShopsDto): Promise<{
    success: boolean;
    data: ShopListItemDto[];
  }> {
    try {
      const shops = await this.shopsService.searchByBounds(searchDto);
      return {
        success: true,
        data: shops,
      };
    } catch (error) {
      console.error('Error in map endpoint:', error);
      throw error;
    }
  }

  @Get()
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async findAll(@Request() req): Promise<{
    success: boolean;
    data: ShopListItemDto[];
  }> {
    try {
      const shops = await this.shopsService.findAll();
      return {
        success: true,
        data: shops,
      };
    } catch (error) {
      console.error('Error in findAll endpoint:', error);
      throw error;
    }
  }

  @Get(':shopId/products')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Shop owner: list products' })
  async listShopProducts(
    @Param('shopId') shopId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    const data = await this.shopCommerce.listProducts(
      shopId,
      req.user.sub,
      req.user.role,
    );
    return { success: true, data };
  }

  @Post(':shopId/products')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Shop owner: create/update product' })
  async saveShopProduct(
    @Param('shopId') shopId: string,
    @Body() body: Record<string, unknown>,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    const data = await this.shopCommerce.upsertProduct(
      shopId,
      body,
      req.user.sub,
      req.user.role,
    );
    return { success: true, data };
  }

  @Get(':shopId/orders')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Shop owner: list orders' })
  async listShopOrders(
    @Param('shopId') shopId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    const data = await this.shopCommerce.listOrders(
      shopId,
      req.user.sub,
      req.user.role,
    );
    return { success: true, data };
  }

  @Post(':shopId/orders')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Shop owner: create/update order' })
  async saveShopOrder(
    @Param('shopId') shopId: string,
    @Body() body: Record<string, unknown>,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    const data = await this.shopCommerce.upsertOrder(
      shopId,
      body,
      req.user.sub,
      req.user.role,
    );
    return { success: true, data };
  }

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  async findOne(
    @Param('id', new ParseUUIDPipe()) id: string,
  ): Promise<{
    success: boolean;
    data: ShopListItemDto;
  }> {
    try {
      const shop = await this.shopsService.findOne(id);
      return {
        success: true,
        data: shop,
      };
    } catch (error) {
      console.error('Error in findOne endpoint:', error);
      throw error;
    }
  }

  @Post()
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.CREATED)
  async create(
    @Body() createDto: CreateShopDto,
    @Request() req,
  ): Promise<{
    success: boolean;
    data: ShopListItemDto;
  }> {
    try {
      const shop = await this.shopsService.create(createDto, req.user.id);
      const shopDto = await this.shopsService.findOne(shop.id);
      return {
        success: true,
        data: shopDto,
      };
    } catch (error) {
      console.error('Error in create endpoint:', error);
      throw error;
    }
  }

  @Put(':id')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async update(
    @Param('id') id: string,
    @Body() updateDto: UpdateShopDto,
    @Request() req,
  ): Promise<{
    success: boolean;
    data: ShopListItemDto;
  }> {
    try {
      await this.shopsService.update(id, updateDto);
      const shop = await this.shopsService.findOne(id);
      return {
        success: true,
        data: shop,
      };
    } catch (error) {
      console.error('Error in update endpoint:', error);
      throw error;
    }
  }

  @Delete(':id')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async remove(@Param('id') id: string, @Request() req): Promise<{
    success: boolean;
    message: string;
  }> {
    try {
      await this.shopsService.update(id, { isActive: false });
      return {
        success: true,
        message: 'Shop deleted successfully',
      };
    } catch (error) {
      console.error('Error in delete endpoint:', error);
      throw error;
    }
  }
}
