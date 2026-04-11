import { Body, Controller, Get, Headers, Post, Query, Req, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminDataJobsService } from './admin-data-jobs.service';
import { CreateDataJobDto } from './dto/create-data-job.dto';

@ApiTags('admin-data-jobs')
@Controller('admin/data-jobs')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminDataJobsController {
  constructor(private readonly jobsService: AdminDataJobsService) {}

  @Get()
  @ApiOperation({ summary: 'List import/export jobs' })
  @RequirePermissions(Permission.VIEW_AUDIT_LOGS)
  list(@Query('limit') limit?: string) {
    return this.jobsService.list(limit ? Number(limit) : undefined);
  }

  @Post()
  @ApiOperation({ summary: 'Create import/export job' })
  @RequirePermissions(Permission.VIEW_AUDIT_LOGS)
  @DangerousAction('data-job-create')
  @UseGuards(DangerousActionGuard)
  create(
    @Body() dto: CreateDataJobDto,
    @Req() req: any,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.jobsService.create(dto, {
      adminId: req.user?.sub,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: forwardedFor ?? null,
    });
  }
}
