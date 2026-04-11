import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminSubscriptionPlanEntity } from './entities/admin-subscription-plan.entity';
import { CreateSubscriptionPlanDto, UpdateSubscriptionPlanDto } from './dto/subscription-plan.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminBillingPlansService {
  constructor(
    @InjectRepository(AdminSubscriptionPlanEntity)
    private readonly repo: Repository<AdminSubscriptionPlanEntity>,
    private readonly audit: AuditLogService,
  ) {}

  async list(params: { limit?: number; offset?: number; activeOnly?: boolean }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);
    const qb = this.repo.createQueryBuilder('p').orderBy('p.code', 'ASC').skip(offset).take(limit);
    if (params.activeOnly) qb.andWhere('p.active = true');
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async getOne(id: string) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Plan not found');
    return row;
  }

  async create(
    dto: CreateSubscriptionPlanDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const exists = await this.repo.findOne({ where: { code: dto.code } });
    if (exists) throw new ConflictException('Plan code already exists');
    const row = this.repo.create({
      code: dto.code,
      name: dto.name,
      description: dto.description ?? null,
      priceCents: dto.priceCents,
      currency: dto.currency ?? 'USD',
      billingInterval: dto.billingInterval ?? 'monthly',
      active: dto.active ?? true,
      features: dto.features ?? null,
    });
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.subscription_plan.create',
      targetType: 'subscription_plan',
      targetId: saved.id,
      after: { code: saved.code },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async update(
    id: string,
    dto: UpdateSubscriptionPlanDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Plan not found');
    const before = { ...row };
    if (dto.name !== undefined) row.name = dto.name;
    if (dto.description !== undefined) row.description = dto.description;
    if (dto.priceCents !== undefined) row.priceCents = dto.priceCents;
    if (dto.currency !== undefined) row.currency = dto.currency;
    if (dto.billingInterval !== undefined) row.billingInterval = dto.billingInterval;
    if (dto.active !== undefined) row.active = dto.active;
    if (dto.features !== undefined) row.features = dto.features;
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.subscription_plan.update',
      targetType: 'subscription_plan',
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
    if (!row) throw new NotFoundException('Plan not found');
    await this.repo.remove(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.subscription_plan.delete',
      targetType: 'subscription_plan',
      targetId: id,
      before: row as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: actor.reason ?? null,
    });
    return { ok: true, id };
  }
}
