import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminIntegrationEntity } from './entities/admin-integration.entity';
import { PatchIntegrationDto, UpsertIntegrationDto } from './dto/integration.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminIntegrationsService {
  constructor(
    @InjectRepository(AdminIntegrationEntity)
    private readonly repo: Repository<AdminIntegrationEntity>,
    private readonly audit: AuditLogService,
  ) {}

  async list() {
    const items = await this.repo.find({ order: { key: 'ASC' } });
    return { items };
  }

  async getOne(id: string) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Integration not found');
    return row;
  }

  async upsert(
    dto: UpsertIntegrationDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    let row = await this.repo.findOne({ where: { key: dto.key } });
    if (!row) {
      row = this.repo.create({
        key: dto.key,
        displayName: dto.displayName,
        enabled: dto.enabled ?? false,
        config: dto.config ?? null,
      });
    } else {
      row.displayName = dto.displayName;
      if (dto.enabled !== undefined) row.enabled = dto.enabled;
      if (dto.config !== undefined) row.config = dto.config;
    }
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.integration.upsert',
      targetType: 'integration',
      targetId: saved.id,
      after: { key: saved.key, enabled: saved.enabled },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async patch(
    id: string,
    dto: PatchIntegrationDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Integration not found');
    const before = { ...row };
    if (dto.displayName !== undefined) row.displayName = dto.displayName;
    if (dto.enabled !== undefined) row.enabled = dto.enabled;
    if (dto.config !== undefined) row.config = dto.config;
    if (dto.lastCheckStatus !== undefined) row.lastCheckStatus = dto.lastCheckStatus;
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.integration.patch',
      targetType: 'integration',
      targetId: id,
      before: before as unknown as Record<string, unknown>,
      after: saved as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async remove(
    id: string,
    actor: { adminId?: string; ip?: string; userAgent?: string; reason?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Integration not found');
    await this.repo.remove(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.integration.delete',
      targetType: 'integration',
      targetId: id,
      before: row as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: actor.reason ?? null,
    });
    return { ok: true, id };
  }
}
