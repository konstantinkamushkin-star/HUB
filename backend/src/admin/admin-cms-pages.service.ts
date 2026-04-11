import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminCmsPageEntity } from './entities/admin-cms-page.entity';
import { CreateCmsPageDto, UpdateCmsPageDto } from './dto/cms-page.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminCmsPagesService {
  constructor(
    @InjectRepository(AdminCmsPageEntity)
    private readonly repo: Repository<AdminCmsPageEntity>,
    private readonly audit: AuditLogService,
  ) {}

  async list(params: { limit?: number; offset?: number; status?: string; locale?: string }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);
    const qb = this.repo.createQueryBuilder('p').orderBy('p.updatedAt', 'DESC').skip(offset).take(limit);
    if (params.status) qb.andWhere('p.status = :st', { st: params.status });
    if (params.locale) qb.andWhere('p.locale = :loc', { loc: params.locale });
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async getOne(id: string) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Page not found');
    return row;
  }

  async getBySlug(slug: string, locale: string) {
    const row = await this.repo.findOne({ where: { slug, locale } });
    if (!row) throw new NotFoundException('Page not found');
    return row;
  }

  async create(
    dto: CreateCmsPageDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const locale = dto.locale ?? 'ru';
    const exists = await this.repo.findOne({ where: { slug: dto.slug, locale } });
    if (exists) throw new ConflictException('Slug already exists for locale');
    const status = dto.status ?? 'draft';
    const row = this.repo.create({
      slug: dto.slug,
      locale,
      title: dto.title,
      body: dto.body ?? '',
      status,
      publishedAt: status === 'published' ? new Date() : null,
    });
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.cms_page.create',
      targetType: 'cms_page',
      targetId: saved.id,
      after: { slug: saved.slug, locale: saved.locale },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async update(
    id: string,
    dto: UpdateCmsPageDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Page not found');
    const before = { ...row };
    if (dto.slug !== undefined || dto.locale !== undefined) {
      const slug = dto.slug ?? row.slug;
      const locale = dto.locale ?? row.locale;
      const clash = await this.repo.findOne({ where: { slug, locale } });
      if (clash && clash.id !== id) throw new ConflictException('Slug already exists for locale');
      row.slug = slug;
      row.locale = locale;
    }
    if (dto.title !== undefined) row.title = dto.title;
    if (dto.body !== undefined) row.body = dto.body;
    if (dto.status !== undefined) {
      row.status = dto.status;
      if (dto.status === 'published' && !row.publishedAt) row.publishedAt = new Date();
      if (dto.status !== 'published') row.publishedAt = null;
    }
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.cms_page.update',
      targetType: 'cms_page',
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
    if (!row) throw new NotFoundException('Page not found');
    await this.repo.remove(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.cms_page.delete',
      targetType: 'cms_page',
      targetId: id,
      before: row as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: actor.reason ?? null,
    });
    return { ok: true, id };
  }
}
