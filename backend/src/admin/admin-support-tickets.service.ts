import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminSupportTicketEntity } from './entities/admin-support-ticket.entity';
import { CreateSupportTicketDto, UpdateSupportTicketDto } from './dto/support-ticket.dto';
import { AuditLogService } from './audit-log.service';

@Injectable()
export class AdminSupportTicketsService {
  constructor(
    @InjectRepository(AdminSupportTicketEntity)
    private readonly repo: Repository<AdminSupportTicketEntity>,
    private readonly audit: AuditLogService,
  ) {}

  async list(params: {
    limit?: number;
    offset?: number;
    status?: string;
    priority?: string;
    assignedAdminId?: string;
  }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);
    const qb = this.repo.createQueryBuilder('t').orderBy('t.createdAt', 'DESC').skip(offset).take(limit);
    if (params.status) qb.andWhere('t.status = :st', { st: params.status });
    if (params.priority) qb.andWhere('t.priority = :pr', { pr: params.priority });
    if (params.assignedAdminId) {
      qb.andWhere('t.assignedAdminId = :aid', { aid: params.assignedAdminId });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async getOne(id: string) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Ticket not found');
    return row;
  }

  async create(
    dto: CreateSupportTicketDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = this.repo.create({
      reporterUserId: dto.reporterUserId ?? null,
      reporterEmail: dto.reporterEmail ?? null,
      subject: dto.subject,
      body: dto.body,
      priority: dto.priority ?? 'normal',
      status: 'open',
    });
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.support_ticket.create',
      targetType: 'support_ticket',
      targetId: saved.id,
      after: { subject: saved.subject },
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }

  async update(
    id: string,
    dto: UpdateSupportTicketDto,
    actor: { adminId?: string; ip?: string; userAgent?: string },
  ) {
    const row = await this.repo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Ticket not found');
    const before = { ...row };
    if (dto.status !== undefined) row.status = dto.status;
    if (dto.priority !== undefined) row.priority = dto.priority;
    if (dto.assignedAdminId !== undefined) row.assignedAdminId = dto.assignedAdminId;
    if (dto.resolutionNote !== undefined) row.resolutionNote = dto.resolutionNote;
    const saved = await this.repo.save(row);
    await this.audit.write({
      adminId: actor.adminId ?? null,
      action: 'admin.support_ticket.update',
      targetType: 'support_ticket',
      targetId: id,
      before: before as unknown as Record<string, unknown>,
      after: saved as unknown as Record<string, unknown>,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
    });
    return saved;
  }
}
