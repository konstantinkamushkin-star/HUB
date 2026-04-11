import { Body, Controller, Get, Headers, Param, Patch, Query, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminReportsService } from './admin-reports.service';
import { UpdateReportStatusDto } from './dto/update-report-status.dto';

@ApiTags('admin-reports')
@Controller('admin/reports')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminReportsController {
  constructor(private readonly reportsService: AdminReportsService) {}

  @Get()
  @ApiOperation({ summary: 'List moderation reports' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  list(
    @Query('status') status?: string,
    @Query('priority') priority?: string,
    @Query('targetType') targetType?: string,
    @Query('limit') limit?: string,
  ) {
    return this.reportsService.list({
      status,
      priority,
      targetType,
      limit: limit ? Number(limit) : undefined,
    });
  }

  @Patch(':id/status')
  @ApiOperation({ summary: 'Update report moderation status' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('report-status-update')
  @UseGuards(DangerousActionGuard)
  updateStatus(
    @Param('id') id: string,
    @Body() dto: UpdateReportStatusDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.reportsService.updateStatus(id, dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      ip: forwardedFor ?? null,
      correlationId: correlationId ?? null,
    });
  }
}
