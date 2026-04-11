import { Body, Controller, Headers, Param, Patch, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { ModerationActionDto } from './dto/moderation-action.dto';
import { AdminModerationService } from './admin-moderation.service';

@ApiTags('admin-moderation')
@Controller('admin/moderation')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminModerationController {
  constructor(private readonly moderationService: AdminModerationService) {}

  private actor(req: any, headers: { userAgent?: string; correlationId?: string; forwardedFor?: string }) {
    return {
      adminId: req.user?.sub,
      userAgent: headers.userAgent ?? null,
      correlationId: headers.correlationId ?? null,
      ip: headers.forwardedFor ?? null,
    };
  }

  @Patch('posts/:id/hide')
  @ApiOperation({ summary: 'Hide feed post' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('hide-post')
  @UseGuards(DangerousActionGuard)
  hidePost(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.hidePost(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('posts/:id/restore')
  @ApiOperation({ summary: 'Restore feed post visibility' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('restore-post')
  @UseGuards(DangerousActionGuard)
  restorePost(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.restorePost(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('comments/:id/hide')
  @ApiOperation({ summary: 'Hide feed comment' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('hide-comment')
  @UseGuards(DangerousActionGuard)
  hideComment(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.hideComment(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('comments/:id/restore')
  @ApiOperation({ summary: 'Restore feed comment' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('restore-comment')
  @UseGuards(DangerousActionGuard)
  restoreComment(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.restoreComment(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('dive-logs/:id/hide')
  @ApiOperation({ summary: 'Hide dive log' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('hide-dive-log')
  @UseGuards(DangerousActionGuard)
  hideDiveLog(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.hideDiveLog(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('dive-logs/:id/restore')
  @ApiOperation({ summary: 'Restore dive log' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  @DangerousAction('restore-dive-log')
  @UseGuards(DangerousActionGuard)
  restoreDiveLog(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.restoreDiveLog(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('dive-centers/:id/verify')
  @ApiOperation({ summary: 'Verify dive center' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  @DangerousAction('verify-dive-center')
  @UseGuards(DangerousActionGuard)
  verifyCenter(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.verifyCenter(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Patch('dive-centers/:id/reject')
  @ApiOperation({ summary: 'Reject dive center verification' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  @DangerousAction('reject-dive-center')
  @UseGuards(DangerousActionGuard)
  rejectCenter(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.moderationService.rejectCenter(id, dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }
}
