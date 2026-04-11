import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { MarineSpeciesEntity } from './entities/marine-species.entity';
import { CreateMarineSpeciesDto, UpdateMarineSpeciesDto } from './dto/marine-species.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminMarineSpeciesService {
  constructor(
    @InjectRepository(MarineSpeciesEntity)
    private readonly repo: Repository<MarineSpeciesEntity>,
    private readonly audit: AuditLogService,
  ) {}

  async list(params: { limit?: number; offset?: number; status?: string; query?: string }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);
    const qb = this.repo.createQueryBuilder('m').orderBy('m.commonName', 'ASC').skip(offset).take(limit);
    if (params.status) qb.andWhere('m.status = :st', { st: params.status });
    if (params.query?.trim()) {
      const q = `%${params.query.trim()}%`;
      qb.andWhere(
        '(LOWER(m.commonName) LIKE LOWER(:q) OR LOWER(m.scientificName) LIKE LOWER(:q))',
        { q },
      );
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async getOne(id: string) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Species not found');
    return row;
  }

  async create(
    dto: CreateMarineSpeciesDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = this.repo.create({
      scientificName: dto.scientificName,
      commonName: dto.commonName,
      family: dto.family ?? null,
      description: dto.description ?? null,
      photoUrl: dto.photoUrl ?? null,
      status: dto.status ?? 'published',
    });
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.marine_species.create',
      targetType: 'marine_species',
      targetId: saved.id,
      after: { id: saved.id, commonName: saved.commonName },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async update(
    id: string,
    dto: UpdateMarineSpeciesDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Species not found');
    const before = { ...row };
    Object.assign(row, dto);
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.marine_species.update',
      targetType: 'marine_species',
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
    if (!row) throw new NotFoundException('Species not found');
    await this.repo.remove(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.marine_species.delete',
      targetType: 'marine_species',
      targetId: id,
      before: row as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: actor.reason ?? null,
    });
    return { ok: true, id };
  }
}
