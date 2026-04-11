import { Body, Controller, Get, Headers, Post, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminSystemSettingsService } from './admin-system-settings.service';
import { UpsertSystemSettingDto } from './dto/upsert-system-setting.dto';

@ApiTags('admin-system-settings')
@Controller('admin/system-settings')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminSystemSettingsController {
  constructor(private readonly settingsService: AdminSystemSettingsService) {}

  @Get()
  @ApiOperation({ summary: 'List system settings' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  list() {
    return this.settingsService.list();
  }

  @Post()
  @ApiOperation({ summary: 'Create or update system setting' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  @DangerousAction('system-setting-upsert')
  @UseGuards(DangerousActionGuard)
  upsert(
    @Body() dto: UpsertSystemSettingDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.settingsService.upsert(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
