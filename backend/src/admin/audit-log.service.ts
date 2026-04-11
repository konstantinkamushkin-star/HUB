import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminAuditLogEntity } from './entities/audit-log.entity';

export type AuditLogWriteInput = {
  adminId?: string | null;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
  ip?: string | null;
  device?: string | null;
  outcome?: string;
  reason?: string | null;
  correlationId?: string | null;
};

@Injectable()
export class AuditLogService {
  constructor(
    @InjectRepository(AdminAuditLogEntity)
    private readonly auditLogRepo: Repository<AdminAuditLogEntity>,
  ) {}

  async write(input: AuditLogWriteInput) {
    const row = this.auditLogRepo.create({
      adminId: input.adminId ?? null,
      action: input.action,
      targetType: input.targetType ?? null,
      targetId: input.targetId ?? null,
      before: input.before ?? null,
      after: input.after ?? null,
      ip: input.ip ?? null,
      device: input.device ?? null,
      outcome: input.outcome ?? 'success',
      reason: input.reason ?? null,
      correlationId: input.correlationId ?? null,
    });
    return this.auditLogRepo.save(row);
  }

  async list(params: {
    adminId?: string;
    action?: string;
    targetType?: string;
    targetId?: string;
    limit?: number;
  }) {
    const qb = this.auditLogRepo
      .createQueryBuilder('l')
      .orderBy('l.createdAt', 'DESC')
      .take(Math.min(Math.max(params.limit ?? 100, 1), 500));

    if (params.adminId) qb.andWhere('l.adminId = :adminId', { adminId: params.adminId });
    if (params.action) qb.andWhere('l.action = :action', { action: params.action });
    if (params.targetType) qb.andWhere('l.targetType = :targetType', { targetType: params.targetType });
    if (params.targetId) qb.andWhere('l.targetId = :targetId', { targetId: params.targetId });

    return qb.getMany();
  }
}
