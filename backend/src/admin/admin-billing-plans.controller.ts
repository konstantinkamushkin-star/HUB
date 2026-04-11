import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminBillingPlansService } from './admin-billing-plans.service';
import { CreateSubscriptionPlanDto, UpdateSubscriptionPlanDto } from './dto/subscription-plan.dto';
import { ModerationActionDto } from './dto/moderation-action.dto';

@ApiTags('admin-billing')
@Controller('admin/billing/plans')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminBillingPlansController {
  constructor(private readonly service: AdminBillingPlansService) {}

  @Get()
  @ApiOperation({ summary: 'List subscription plan catalog' })
  @RequirePermissions(Permission.MANAGE_BILLING)
  list(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('activeOnly') activeOnly?: string,
  ) {
    return this.service.list({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      activeOnly: activeOnly === 'true' || activeOnly === '1',
    });
  }

  @Get(':id')
  @RequirePermissions(Permission.MANAGE_BILLING)
  getOne(@Param('id') id: string) {
    return this.service.getOne(id);
  }

  @Post()
  @RequirePermissions(Permission.MANAGE_BILLING)
  create(
    @Body() dto: CreateSubscriptionPlanDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.create(dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Patch(':id')
  @RequirePermissions(Permission.MANAGE_BILLING)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateSubscriptionPlanDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.update(id, dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Delete(':id')
  @DangerousAction('subscription-plan-delete')
  @UseGuards(DangerousActionGuard)
  @RequirePermissions(Permission.MANAGE_BILLING)
  remove(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.remove(id, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
      reason: dto.reason,
    });
  }
}
