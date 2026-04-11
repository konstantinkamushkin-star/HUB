import { Body, Controller, Headers, Post, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminMergeService } from './admin-merge.service';
import { MergeEntitiesDto } from './dto/merge-entities.dto';

@ApiTags('admin-merge')
@Controller('admin/merge')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminMergeController {
  constructor(private readonly mergeService: AdminMergeService) {}

  private actor(req: any, h: any) {
    return {
      adminId: req.user?.sub,
      userAgent: h.userAgent ?? null,
      correlationId: h.correlationId ?? null,
      ip: h.forwardedFor ?? null,
    };
  }

  @Post('users')
  @ApiOperation({ summary: 'Merge duplicate users' })
  @RequirePermissions(Permission.MERGE_ENTITIES)
  @DangerousAction('merge-users')
  @UseGuards(DangerousActionGuard)
  mergeUsers(
    @Body() dto: MergeEntitiesDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.mergeService.mergeUsers(dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Post('dive-centers')
  @ApiOperation({ summary: 'Merge duplicate dive centers' })
  @RequirePermissions(Permission.MERGE_ENTITIES)
  @DangerousAction('merge-dive-centers')
  @UseGuards(DangerousActionGuard)
  mergeDiveCenters(
    @Body() dto: MergeEntitiesDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.mergeService.mergeDiveCenters(dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }

  @Post('dive-sites')
  @ApiOperation({ summary: 'Merge duplicate dive sites' })
  @RequirePermissions(Permission.MERGE_ENTITIES)
  @DangerousAction('merge-dive-sites')
  @UseGuards(DangerousActionGuard)
  mergeDiveSites(
    @Body() dto: MergeEntitiesDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.mergeService.mergeDiveSites(dto, this.actor(req, { userAgent, correlationId, forwardedFor }));
  }
}
