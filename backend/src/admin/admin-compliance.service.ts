import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminComplianceRequestEntity } from './entities/admin-compliance-request.entity';
import { CreateComplianceRequestDto } from './dto/create-compliance-request.dto';
import { UpdateComplianceRequestDto } from './dto/update-compliance-request.dto';
import { AuditLogService } from './audit-log.service';
import { User } from '../users/entities/user.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';

@Injectable()
export class AdminComplianceService {
  constructor(
    @InjectRepository(AdminComplianceRequestEntity)
    private readonly requestsRepo: Repository<AdminComplianceRequestEntity>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(FeedPost)
    private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(FeedPostComment)
    private readonly commentsRepo: Repository<FeedPostComment>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  list(limit = 100) {
    return this.requestsRepo.find({
      order: { createdAt: 'DESC' },
      take: Math.min(Math.max(limit, 1), 500),
    });
  }

  async create(dto: CreateComplianceRequestDto, actor: any) {
    const row = this.requestsRepo.create({
      userId: dto.userId,
      type: dto.type,
      reason: dto.reason ?? null,
      status: 'pending',
      payload: null,
      handledByAdminId: null,
    });
    const saved = await this.requestsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.compliance_request.create',
      targetType: 'compliance_request',
      targetId: saved.id,
      after: { userId: saved.userId, type: saved.type, status: saved.status },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }

  async update(id: string, dto: UpdateComplianceRequestDto, actor: any) {
    const row = await this.requestsRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Compliance request not found');
    const before = { status: row.status, reason: row.reason };
    row.status = dto.status;
    row.reason = dto.reason ?? row.reason;
    row.handledByAdminId = actor?.adminId ?? null;

    if (dto.status === 'completed' && row.type === 'export_data') {
      row.payload = await this.buildUserExportPayload(row.userId);
    }
    if (dto.status === 'completed' && row.type === 'delete_data') {
      await this.performUserDelete(row.userId);
      row.payload = { deleted: true, deletedAt: new Date().toISOString() };
    }

    const saved = await this.requestsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.compliance_request.update',
      targetType: 'compliance_request',
      targetId: saved.id,
      before,
      after: { status: saved.status, reason: saved.reason },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }

  private async buildUserExportPayload(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    const posts = await this.postsRepo.count({ where: { userId } });
    const comments = await this.commentsRepo.count({ where: { userId } });
    const logs = await this.logsRepo.count({ where: { userId } });

    return {
      generatedAt: new Date().toISOString(),
      user,
      stats: { posts, comments, logs },
    };
  }

  private async performUserDelete(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) return;
    user.accountStatus = 'deleted';
    user.deletedAt = new Date();
    await this.usersRepo.save(user);
  }
}
