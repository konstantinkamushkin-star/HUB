import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { ErrorStatsService } from './error-stats.service';
import { AdminDashboardService } from './admin-dashboard.service';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';

@ApiTags('admin')
@Controller('admin')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminController {
  constructor(
    private readonly errorStatsService: ErrorStatsService,
    private readonly adminDashboardService: AdminDashboardService,
  ) {}

  @Get('dashboard/overview')
  @ApiOperation({ summary: 'Aggregated metrics for admin dashboard' })
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  getDashboardOverview() {
    return this.adminDashboardService.getOverview();
  }

  @Get('error-stats')
  @ApiOperation({ summary: 'Get backend error stats (SUPER_ADMIN only)' })
  @RequirePermissions(Permission.VIEW_ERROR_STATS)
  getErrorStats() {
    return this.errorStatsService.getStats();
  }
}
