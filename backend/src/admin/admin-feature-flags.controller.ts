import { Body, Controller, Get, Headers, Post, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminFeatureFlagsService } from './admin-feature-flags.service';
import { UpsertFeatureFlagDto } from './dto/upsert-feature-flag.dto';

@ApiTags('admin-feature-flags')
@Controller('admin/feature-flags')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminFeatureFlagsController {
  constructor(private readonly featureFlagsService: AdminFeatureFlagsService) {}

  @Get()
  @ApiOperation({ summary: 'List feature flags' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  list() {
    return this.featureFlagsService.list();
  }

  @Post()
  @ApiOperation({ summary: 'Create or update feature flag' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  @DangerousAction('feature-flag-upsert')
  @UseGuards(DangerousActionGuard)
  upsert(
    @Body() dto: UpsertFeatureFlagDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.featureFlagsService.upsert(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
