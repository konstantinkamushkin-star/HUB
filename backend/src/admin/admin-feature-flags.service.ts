import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminFeatureFlagEntity } from './entities/feature-flag.entity';
import { UpsertFeatureFlagDto } from './dto/upsert-feature-flag.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminFeatureFlagsService {
  constructor(
    @InjectRepository(AdminFeatureFlagEntity)
    private readonly flagsRepo: Repository<AdminFeatureFlagEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  list() {
    return this.flagsRepo.find({ order: { key: 'ASC' } });
  }

  async upsert(dto: UpsertFeatureFlagDto, actor: any) {
    let row = await this.flagsRepo.findOne({ where: { key: dto.key } });
    const before = row
      ? {
          enabled: row.enabled,
          rolloutRules: row.rolloutRules,
          description: row.description,
        }
      : null;

    if (!row) {
      row = this.flagsRepo.create({
        key: dto.key,
        enabled: dto.enabled,
        rolloutRules: dto.rolloutRules ?? null,
        description: dto.description ?? null,
        updatedByAdminId: actor?.adminId ?? null,
      });
    } else {
      row.enabled = dto.enabled;
      row.rolloutRules = dto.rolloutRules ?? null;
      row.description = dto.description ?? null;
      row.updatedByAdminId = actor?.adminId ?? null;
    }

    const saved = await this.flagsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.feature_flag.upsert',
      targetType: 'feature_flag',
      targetId: saved.key,
      before,
      after: {
        enabled: saved.enabled,
        rolloutRules: saved.rolloutRules,
        description: saved.description,
      },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }
}
