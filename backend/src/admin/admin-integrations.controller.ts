import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Patch,
  Post,
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
import { AdminIntegrationsService } from './admin-integrations.service';
import {
  PatchIntegrationDangerousDto,
  UpsertIntegrationDangerousDto,
} from './dto/integration.dto';
import { ModerationActionDto } from './dto/moderation-action.dto';

@ApiTags('admin-integrations')
@Controller('admin/integrations')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminIntegrationsController {
  constructor(private readonly service: AdminIntegrationsService) {}

  @Get()
  @ApiOperation({ summary: 'List integration configs' })
  @RequirePermissions(Permission.MANAGE_INTEGRATIONS)
  list() {
    return this.service.list();
  }

  @Get(':id')
  @RequirePermissions(Permission.MANAGE_INTEGRATIONS)
  getOne(@Param('id') id: string) {
    return this.service.getOne(id);
  }

  @Post()
  @ApiOperation({ summary: 'Create or replace integration by key' })
  @RequirePermissions(Permission.MANAGE_INTEGRATIONS)
  @DangerousAction('integration-upsert')
  @UseGuards(DangerousActionGuard)
  upsert(
    @Body() body: UpsertIntegrationDangerousDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    const { reason: _r, ...dto } = body;
    return this.service.upsert(dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Patch(':id')
  @RequirePermissions(Permission.MANAGE_INTEGRATIONS)
  @DangerousAction('integration-patch')
  @UseGuards(DangerousActionGuard)
  patch(
    @Param('id') id: string,
    @Body() body: PatchIntegrationDangerousDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    const { reason: _r, ...dto } = body;
    return this.service.patch(id, dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Delete(':id')
  @RequirePermissions(Permission.MANAGE_INTEGRATIONS)
  @DangerousAction('integration-delete')
  @UseGuards(DangerousActionGuard)
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
