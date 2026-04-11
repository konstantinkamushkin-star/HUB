import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  HttpCode,
  HttpStatus,
  UseGuards,
  Request,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { ShopsService } from './shops.service';
import {
  SearchShopsDto,
  MapSearchShopsDto,
  PopularShopsDto,
} from './dto/search-shops.dto';
import { CreateShopDto, UpdateShopDto } from './dto/create-shop.dto';
import { ShopSearchResultDto, ShopListItemDto } from './dto/shop-response.dto';

@Controller('v1/shops')
export class ShopsController {
  constructor(private readonly shopsService: ShopsService) {
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

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  async findOne(@Param('id') id: string): Promise<{
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
