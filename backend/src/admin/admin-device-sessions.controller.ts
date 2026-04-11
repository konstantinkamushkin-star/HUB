import { Controller, Get, Param, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UserPushDevice } from '../push/entities/user-push-device.entity';

@ApiTags('admin-device-sessions')
@Controller('admin/device-sessions')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminDeviceSessionsController {
  constructor(
    @InjectRepository(UserPushDevice)
    private readonly devicesRepo: Repository<UserPushDevice>,
  ) {}

  @Get('users/:userId/devices')
  @ApiOperation({ summary: 'List user devices/sessions (push devices as session proxies)' })
  @RequirePermissions(Permission.MANAGE_USERS)
  async listUserDevices(@Param('userId') userId: string) {
    const devices = await this.devicesRepo.find({
      where: { userId },
      order: { updatedAt: 'DESC' },
    });
    return {
      userId,
      activeSessions: devices.length,
      devices: devices.map((d) => ({
        id: d.id,
        platform: d.platform,
        tokenMasked: d.token.slice(0, 8) + '...',
        lastSeenAt: d.updatedAt,
      })),
    };
  }
}
