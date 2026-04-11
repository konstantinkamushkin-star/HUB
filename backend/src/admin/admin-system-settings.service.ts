import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminSystemSettingEntity } from './entities/system-setting.entity';
import { UpsertSystemSettingDto } from './dto/upsert-system-setting.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminSystemSettingsService {
  constructor(
    @InjectRepository(AdminSystemSettingEntity)
    private readonly settingsRepo: Repository<AdminSystemSettingEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  list() {
    return this.settingsRepo.find({ order: { key: 'ASC' } });
  }

  async upsert(dto: UpsertSystemSettingDto, actor: any) {
    let row = await this.settingsRepo.findOne({ where: { key: dto.key } });
    const before = row
      ? { value: row.value, isSensitive: row.isSensitive }
      : null;

    if (!row) {
      row = this.settingsRepo.create({
        key: dto.key,
        value: dto.value,
        isSensitive: dto.isSensitive ?? false,
        updatedByAdminId: actor?.adminId ?? null,
      });
    } else {
      row.value = dto.value;
      if (typeof dto.isSensitive === 'boolean') row.isSensitive = dto.isSensitive;
      row.updatedByAdminId = actor?.adminId ?? null;
    }

    const saved = await this.settingsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.system_setting.upsert',
      targetType: 'system_setting',
      targetId: saved.key,
      before,
      after: { value: saved.value, isSensitive: saved.isSensitive },
      reason: dto.reason,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }
}
