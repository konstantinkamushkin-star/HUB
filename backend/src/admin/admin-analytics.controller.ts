import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AdminAnalyticsService } from './admin-analytics.service';

@ApiTags('admin-analytics')
@Controller('admin/analytics')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminAnalyticsController {
  constructor(private readonly analytics: AdminAnalyticsService) {}

  @Get('summary')
  @ApiOperation({ summary: 'Extended analytics: dashboard + TZ module metrics' })
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  summary() {
    return this.analytics.summary();
  }

  @Get('events')
  @ApiOperation({ summary: 'List ingested analytics_events rows' })
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  listEvents(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('name') name?: string,
  ) {
    return this.analytics.listStoredEvents({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      name,
    });
  }
}
