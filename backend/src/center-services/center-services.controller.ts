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
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CenterServicesService } from './center-services.service';
import { CreateCenterServiceDto } from './dto/create-center-service.dto';
import { UpdateCenterServiceDto } from './dto/update-center-service.dto';

@ApiTags('center-services')
@Controller('center-services')
export class CenterServicesController {
  constructor(private readonly centerServicesService: CenterServicesService) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'List dive center services catalog' })
  async list(
    @Query('diveCenterId') diveCenterId: string,
    @Query('includeInactive') includeInactive?: string,
  ) {
    return this.centerServicesService.listByCenter(
      diveCenterId,
      includeInactive === 'true',
    );
  }

  @Post()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Create dive center service (admin/owner)' })
  async create(
    @Request() req: { user: { sub: string; role?: string } },
    @Body() dto: CreateCenterServiceDto,
  ) {
    return this.centerServicesService.create(req.user.sub, req.user.role, dto);
  }

  @Patch(':serviceId')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update dive center service (admin/owner)' })
  async update(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('serviceId') serviceId: string,
    @Body() dto: UpdateCenterServiceDto,
  ) {
    return this.centerServicesService.update(
      req.user.sub,
      req.user.role,
      serviceId,
      dto,
    );
  }

  @Delete(':serviceId')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Delete dive center service (admin/owner)' })
  async remove(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('serviceId') serviceId: string,
  ) {
    await this.centerServicesService.remove(req.user.sub, req.user.role, serviceId);
  }
}
