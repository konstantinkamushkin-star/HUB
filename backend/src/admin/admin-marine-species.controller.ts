import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminMarineSpeciesService } from './admin-marine-species.service';
import { CreateMarineSpeciesDto, UpdateMarineSpeciesDto } from './dto/marine-species.dto';
import { ModerationActionDto } from './dto/moderation-action.dto';

@ApiTags('admin-marine-species')
@Controller('admin/marine-species')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminMarineSpeciesController {
  constructor(private readonly service: AdminMarineSpeciesService) {}

  @Get()
  @ApiOperation({ summary: 'List marine species' })
  @RequirePermissions(Permission.MANAGE_MARINE_LIFE)
  list(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('status') status?: string,
    @Query('query') query?: string,
  ) {
    return this.service.list({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      status,
      query,
    });
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get marine species by id' })
  @RequirePermissions(Permission.MANAGE_MARINE_LIFE)
  getOne(@Param('id') id: string) {
    return this.service.getOne(id);
  }

  @Post()
  @ApiOperation({ summary: 'Create marine species' })
  @RequirePermissions(Permission.MANAGE_MARINE_LIFE)
  create(
    @Body() dto: CreateMarineSpeciesDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.create(dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Patch(':id')
  @ApiOperation({ summary: 'Update marine species' })
  @RequirePermissions(Permission.MANAGE_MARINE_LIFE)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateMarineSpeciesDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.update(id, dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete marine species' })
  @RequirePermissions(Permission.MANAGE_MARINE_LIFE)
  @DangerousAction('marine-species-delete')
  @UseGuards(DangerousActionGuard)
  remove(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.remove(id, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
      reason: dto.reason,
    });
  }
}
