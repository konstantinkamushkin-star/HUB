import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common';
import { DiveCentersService } from './dive-centers.service';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { AddDiveCenterInstructorDto } from './dto/add-dive-center-instructor.dto';
import { PatchDiveCenterInstructorDto } from './dto/patch-dive-center-instructor.dto';
import {
  SearchDiveCentersDto,
  MapSearchCentersDto,
  PopularDiveCentersDto,
} from './dto/search-dive-centers.dto';
import {
  DiveCenterSearchResultDto,
  DiveCenterListItemDto,
} from './dto/dive-center-response.dto';

@Controller('v1/dive-centers')
export class DiveCentersController {
  constructor(private readonly diveCentersService: DiveCentersService) {
    console.log('✅ DiveCentersController initialized');
  }

  @Get('search')
  @HttpCode(HttpStatus.OK)
  async search(
    @Query() searchDto: SearchDiveCentersDto,
  ): Promise<DiveCenterSearchResultDto> {
    try {
      const result = await this.diveCentersService.searchByLocation(searchDto);
      return result;
    } catch (error) {
      console.error('Error in search endpoint:', error);
      throw error;
    }
  }

  @Get('map')
  @HttpCode(HttpStatus.OK)
  async mapSearch(@Query() searchDto: MapSearchCentersDto): Promise<{
    success: boolean;
    data: DiveCenterListItemDto[];
  }> {
    try {
      const centers = await this.diveCentersService.searchByBounds(searchDto);
      return {
        success: true,
        data: centers,
      };
    } catch (error) {
      console.error('Error in map endpoint:', error);
      throw error;
    }
  }

  @Get('popular')
  @HttpCode(HttpStatus.OK)
  async popular(@Query() searchDto: PopularDiveCentersDto): Promise<{
    success: boolean;
    data: DiveCenterListItemDto[];
  }> {
    try {
      const centers = await this.diveCentersService.getPopular(searchDto);
      return {
        success: true,
        data: centers,
      };
    } catch (error) {
      console.error('Error in popular endpoint:', error);
      throw error;
    }
  }

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  async getPublicById(@Param('id') id: string): Promise<{
    success: boolean;
    data: DiveCenterListItemDto;
  }> {
    const data = await this.diveCentersService.getPublicById(id);
    return { success: true, data };
  }

  @Get(':id/instructors')
  @HttpCode(HttpStatus.OK)
  async getInstructors(@Param('id') id: string) {
    try {
      const instructors = await this.diveCentersService.getInstructors(id);
      return instructors;
    } catch (error) {
      console.error('Error in getInstructors endpoint:', error);
      // Return empty array instead of throwing to prevent breaking the UI
      return [];
    }
  }

  @Post(':id/instructors')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.CREATED)
  async addInstructor(
    @Param('id') id: string,
    @Body() dto: AddDiveCenterInstructorDto,
    @Request() req: { user: { sub: string } },
  ) {
    return this.diveCentersService.addInstructorMember(
      id,
      dto.userId,
      req.user.sub,
    );
  }

  @Patch(':id/instructors/:userId')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async patchInstructor(
    @Param('id') id: string,
    @Param('userId') userId: string,
    @Body() dto: PatchDiveCenterInstructorDto,
    @Request() req: { user: { sub: string } },
  ): Promise<{ ok: boolean }> {
    try {
      await this.diveCentersService.updateInstructorProfile(
        id,
        userId,
        req.user.sub,
        dto,
      );
      return { ok: true };
    } catch (e) {
      throw e;
    }
  }

  @Delete(':id/instructors/:instructorId')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async removeInstructor(
    @Param('id') id: string,
    @Param('instructorId') instructorId: string,
    @Request() req: { user: { sub: string } },
  ): Promise<Record<string, never>> {
    await this.diveCentersService.removeInstructorMember(
      id,
      instructorId,
      req.user.sub,
    );
    return {};
  }
}
