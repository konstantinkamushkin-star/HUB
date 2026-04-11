import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission, ROLE_PERMISSIONS } from '../auth/rbac/permissions';
import { ALL_ADMIN_ROLES } from '../auth/rbac/admin-roles';

@ApiTags('admin-roles')
@Controller('admin/roles')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminRolesController {
  @Get()
  @ApiOperation({ summary: 'List admin roles and permissions matrix' })
  @RequirePermissions(Permission.MANAGE_ROLES)
  listRoles() {
    return ALL_ADMIN_ROLES.map((role) => ({
      role,
      permissions: ROLE_PERMISSIONS[role],
    }));
  }
}
