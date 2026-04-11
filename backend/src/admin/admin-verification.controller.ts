import { Body, Controller, Get, Headers, Param, Patch, Post, Query, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminVerificationService } from './admin-verification.service';
import { CreateVerificationRequestDto } from './dto/create-verification-request.dto';
import { UpdateVerificationRequestDto } from './dto/update-verification-request.dto';

@ApiTags('admin-verification')
@Controller('admin/verification')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminVerificationController {
  constructor(private readonly verificationService: AdminVerificationService) {}

  @Get('requests')
  @ApiOperation({ summary: 'List verification requests history' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  list(@Query('limit') limit?: string) {
    return this.verificationService.list(limit ? Number(limit) : undefined);
  }

  @Post('requests')
  @ApiOperation({ summary: 'Create verification request' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  create(
    @Body() dto: CreateVerificationRequestDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.verificationService.create(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }

  @Patch('requests/:id')
  @ApiOperation({ summary: 'Update verification request status/decision' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  @DangerousAction('verification-request-update')
  @UseGuards(DangerousActionGuard)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateVerificationRequestDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.verificationService.update(id, dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
