import { Body, Controller, Get, Headers, Param, Patch, Post, Query, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AdminSupportTicketsService } from './admin-support-tickets.service';
import { CreateSupportTicketDto, UpdateSupportTicketDto } from './dto/support-ticket.dto';

@ApiTags('admin-support')
@Controller('admin/support/tickets')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminSupportTicketsController {
  constructor(private readonly service: AdminSupportTicketsService) {}

  @Get()
  @ApiOperation({ summary: 'List support tickets' })
  @RequirePermissions(Permission.MANAGE_SUPPORT)
  list(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('status') status?: string,
    @Query('priority') priority?: string,
    @Query('assignedAdminId') assignedAdminId?: string,
  ) {
    return this.service.list({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      status,
      priority,
      assignedAdminId,
    });
  }

  @Get(':id')
  @RequirePermissions(Permission.MANAGE_SUPPORT)
  getOne(@Param('id') id: string) {
    return this.service.getOne(id);
  }

  @Post()
  @RequirePermissions(Permission.MANAGE_SUPPORT)
  create(
    @Body() dto: CreateSupportTicketDto,
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
  @RequirePermissions(Permission.MANAGE_SUPPORT)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateSupportTicketDto,
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
}
