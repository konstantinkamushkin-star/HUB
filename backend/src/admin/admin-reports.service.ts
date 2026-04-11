import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminReportEntity } from './entities/admin-report.entity';
import { UpdateReportStatusDto } from './dto/update-report-status.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminReportsService {
  constructor(
    @InjectRepository(AdminReportEntity)
    private readonly reportsRepo: Repository<AdminReportEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  async list(params: { status?: string; priority?: string; targetType?: string; limit?: number }) {
    const qb = this.reportsRepo
      .createQueryBuilder('r')
      .orderBy('r.createdAt', 'DESC')
      .take(Math.min(Math.max(params.limit ?? 100, 1), 500));

    if (params.status) qb.andWhere('r.status = :status', { status: params.status });
    if (params.priority) qb.andWhere('r.priority = :priority', { priority: params.priority });
    if (params.targetType) qb.andWhere('r.targetType = :targetType', { targetType: params.targetType });
    return qb.getMany();
  }

  async updateStatus(
    reportId: string,
    dto: UpdateReportStatusDto,
    actor: { adminId?: string; ip?: string; userAgent?: string; correlationId?: string },
  ) {
    const report = await this.reportsRepo.findOne({ where: { id: reportId } });
    if (!report) throw new NotFoundException('Report not found');

    const before = { status: report.status, priority: report.priority, resolution: report.resolution };
    report.status = dto.status;
    if (dto.priority) report.priority = dto.priority;
    if (dto.resolution) report.resolution = dto.resolution;
    report.handledByAdminId = actor.adminId ?? null;
    report.history = [
      ...(report.history ?? []),
      {
        at: new Date().toISOString(),
        adminId: actor.adminId ?? null,
        status: dto.status,
        priority: dto.priority ?? report.priority,
        resolution: dto.resolution ?? null,
      },
    ];

    const saved = await this.reportsRepo.save(report);
    await this.auditLogService.write({
      adminId: actor.adminId ?? null,
      action: 'admin.report.status.update',
      targetType: 'report',
      targetId: reportId,
      before,
      after: { status: saved.status, priority: saved.priority, resolution: saved.resolution },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      correlationId: actor.correlationId ?? null,
      reason: dto.resolution ?? null,
    });
    return saved;
  }
}
