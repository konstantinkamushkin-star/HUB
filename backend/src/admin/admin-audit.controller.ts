import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AuditLogService } from './audit-log.service';

@ApiTags('admin-audit')
@Controller('admin/audit-logs')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminAuditController {
  constructor(private readonly auditLogService: AuditLogService) {}

  @Get()
  @ApiOperation({ summary: 'Read admin audit logs' })
  @RequirePermissions(Permission.VIEW_AUDIT_LOGS)
  list(
    @Query('adminId') adminId?: string,
    @Query('action') action?: string,
    @Query('targetType') targetType?: string,
    @Query('targetId') targetId?: string,
    @Query('limit') limit?: string,
  ) {
    return this.auditLogService.list({
      adminId,
      action,
      targetType,
      targetId,
      limit: limit ? Number(limit) : undefined,
    });
  }
}
