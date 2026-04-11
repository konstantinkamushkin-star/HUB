import { Body, Controller, Get, Headers, Param, Patch, Post, Query, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminComplianceService } from './admin-compliance.service';
import { CreateComplianceRequestDto } from './dto/create-compliance-request.dto';
import { UpdateComplianceRequestDto } from './dto/update-compliance-request.dto';

@ApiTags('admin-compliance')
@Controller('admin/compliance')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminComplianceController {
  constructor(private readonly complianceService: AdminComplianceService) {}

  @Get('requests')
  @ApiOperation({ summary: 'List legal/compliance requests' })
  @RequirePermissions(Permission.MANAGE_USERS)
  list(@Query('limit') limit?: string) {
    return this.complianceService.list(limit ? Number(limit) : undefined);
  }

  @Post('requests')
  @ApiOperation({ summary: 'Create compliance request (export/delete user data)' })
  @RequirePermissions(Permission.MANAGE_USERS)
  @DangerousAction('compliance-request-create')
  @UseGuards(DangerousActionGuard)
  create(
    @Body() dto: CreateComplianceRequestDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.complianceService.create(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }

  @Patch('requests/:id')
  @ApiOperation({ summary: 'Update compliance request status' })
  @RequirePermissions(Permission.MANAGE_USERS)
  @DangerousAction('compliance-request-update')
  @UseGuards(DangerousActionGuard)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateComplianceRequestDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.complianceService.update(id, dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
