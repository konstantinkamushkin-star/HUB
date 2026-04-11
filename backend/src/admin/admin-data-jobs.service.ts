import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminDataJobEntity } from './entities/admin-data-job.entity';
import { CreateDataJobDto } from './dto/create-data-job.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminDataJobsService {
  constructor(
    @InjectRepository(AdminDataJobEntity)
    private readonly jobsRepo: Repository<AdminDataJobEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  list(limit = 100) {
    return this.jobsRepo.find({
      order: { createdAt: 'DESC' },
      take: Math.min(Math.max(limit, 1), 500),
    });
  }

  async create(dto: CreateDataJobDto, actor: any) {
    const row = this.jobsRepo.create({
      type: dto.type,
      format: dto.format,
      targetType: dto.targetType,
      status: 'queued',
      filters: dto.filters ?? null,
      resultMeta: null,
      createdByAdminId: actor?.adminId ?? null,
    });
    const saved = await this.jobsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.data_job.create',
      targetType: 'data_job',
      targetId: saved.id,
      after: {
        type: saved.type,
        format: saved.format,
        targetType: saved.targetType,
        status: saved.status,
      },
      reason: dto.reason,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }
}
