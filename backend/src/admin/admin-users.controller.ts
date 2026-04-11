import {
  Body,
  Controller,
  Get,
  Headers,
  Param,
  Patch,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AdminUsersService } from './admin-users.service';
import { UpdateUserStatusDto } from './dto/update-user-status.dto';
import { UpdateUserRoleDto } from './dto/update-user-role.dto';
import { UpdateUserSubscriptionDto } from './dto/update-user-subscription.dto';
import { DangerousActionGuard } from './dangerous-action.guard';
import { DangerousAction } from './dangerous-action.decorator';

@ApiTags('admin-users')
@Controller('admin/users')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminUsersController {
  constructor(private readonly adminUsersService: AdminUsersService) {}

  @Get()
  @ApiOperation({ summary: 'Admin list users with filters' })
  @RequirePermissions(Permission.MANAGE_USERS)
  listUsers(
    @Query('query') query?: string,
    @Query('status') status?: string,
    @Query('role') role?: string,
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
  ) {
    return this.adminUsersService.listUsers({
      query,
      status,
      role,
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
    });
  }

  @Patch(':id/status')
  @ApiOperation({ summary: 'Admin update user account status' })
  @RequirePermissions(Permission.MANAGE_USERS)
  @DangerousAction('user-status-update')
  @UseGuards(DangerousActionGuard)
  updateStatus(
    @Param('id') id: string,
    @Body() dto: UpdateUserStatusDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') xForwardedFor?: string,
  ) {
    return this.adminUsersService.updateStatus(id, dto, {
      adminId: req.user?.sub,
      ip: xForwardedFor ?? null,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
    });
  }

  @Patch(':id/role')
  @ApiOperation({ summary: 'Admin assign role to user' })
  @RequirePermissions(Permission.MANAGE_ROLES)
  @DangerousAction('user-role-update')
  @UseGuards(DangerousActionGuard)
  updateRole(
    @Param('id') id: string,
    @Body() dto: UpdateUserRoleDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') xForwardedFor?: string,
  ) {
    return this.adminUsersService.updateRole(id, dto, {
      adminId: req.user?.sub,
      ip: xForwardedFor ?? null,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
    });
  }

  @Patch(':id/subscription')
  @ApiOperation({ summary: 'Update user subscription tier / expiry (billing)' })
  @RequirePermissions(Permission.MANAGE_BILLING)
  @DangerousAction('user-subscription-update')
  @UseGuards(DangerousActionGuard)
  updateSubscription(
    @Param('id') id: string,
    @Body() dto: UpdateUserSubscriptionDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') xForwardedFor?: string,
  ) {
    return this.adminUsersService.updateSubscription(id, dto, {
      adminId: req.user?.sub,
      ip: xForwardedFor ?? null,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
    });
  }
}
