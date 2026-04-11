import { Body, Controller, Get, Headers, Post, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminNotificationsService } from './admin-notifications.service';
import { CreateNotificationCampaignDto } from './dto/create-notification-campaign.dto';

@ApiTags('admin-notifications')
@Controller('admin/notifications')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminNotificationsController {
  constructor(private readonly notificationsService: AdminNotificationsService) {}

  @Get('campaigns')
  @ApiOperation({ summary: 'List notification campaigns' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  listCampaigns() {
    return this.notificationsService.list();
  }

  @Post('campaigns')
  @ApiOperation({ summary: 'Create and send/schedule notification campaign' })
  @RequirePermissions(Permission.MANAGE_SETTINGS)
  @DangerousAction('notification-campaign-create')
  @UseGuards(DangerousActionGuard)
  createCampaign(
    @Body() dto: CreateNotificationCampaignDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.notificationsService.createCampaign(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
