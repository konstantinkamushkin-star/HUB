import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { serializePublicUser } from '../auth/auth.service';
import { UpdateUserStatusDto } from './dto/update-user-status.dto';
import { UpdateUserRoleDto } from './dto/update-user-role.dto';
import { UpdateUserSubscriptionDto } from './dto/update-user-subscription.dto';
import { AuditLogService } from './audit-log.service';
import { UserAccountStatus } from '../common/statuses';

@Injectable()
export class AdminUsersService {
  constructor(
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    private readonly auditLogService: AuditLogService,
  ) {}

  async listUsers(params: {
    query?: string;
    status?: string;
    role?: string;
    limit?: number;
    offset?: number;
  }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);

    const qb = this.usersRepo
      .createQueryBuilder('u')
      .orderBy('u.createdAt', 'DESC')
      .skip(offset)
      .take(limit);

    if (params.query) {
      const pattern = `%${params.query.trim()}%`;
      qb.andWhere(
        '(LOWER(u.email) LIKE LOWER(:q) OR LOWER(u."firstName") LIKE LOWER(:q) OR LOWER(u."lastName") LIKE LOWER(:q) OR u.id::text = :exactId)',
        { q: pattern, exactId: params.query.trim() },
      );
    }
    if (params.status) {
      qb.andWhere('u."accountStatus" = :status', { status: params.status });
    }
    if (params.role) {
      qb.andWhere('u.role = :role', { role: params.role });
    }

    const [rows, total] = await qb.getManyAndCount();
    return {
      items: rows.map((u) => serializePublicUser(u)),
      total,
      limit,
      offset,
    };
  }

  async updateStatus(
    userId: string,
    dto: UpdateUserStatusDto,
    actor: { adminId?: string; ip?: string; userAgent?: string; correlationId?: string },
  ) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    const before = { accountStatus: user.accountStatus, deletedAt: user.deletedAt ?? null };
    user.accountStatus = dto.status;
    if (dto.status === UserAccountStatus.DELETED) {
      user.deletedAt = new Date();
    } else if (user.deletedAt) {
      user.deletedAt = null;
    }

    const saved = await this.usersRepo.save(user);
    const after = { accountStatus: saved.accountStatus, deletedAt: saved.deletedAt ?? null };

    await this.auditLogService.write({
      adminId: actor.adminId ?? null,
      action: 'admin.user.status.update',
      targetType: 'user',
      targetId: userId,
      before,
      after,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: dto.reason ?? null,
      correlationId: actor.correlationId ?? null,
    });

    return serializePublicUser(saved);
  }

  async updateRole(
    userId: string,
    dto: UpdateUserRoleDto,
    actor: { adminId?: string; ip?: string; userAgent?: string; correlationId?: string },
  ) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    const before = { role: user.role };
    user.role = dto.role;
    const saved = await this.usersRepo.save(user);
    const after = { role: saved.role };

    await this.auditLogService.write({
      adminId: actor.adminId ?? null,
      action: 'admin.user.role.update',
      targetType: 'user',
      targetId: userId,
      before,
      after,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: dto.reason ?? null,
      correlationId: actor.correlationId ?? null,
    });

    return serializePublicUser(saved);
  }

  async updateSubscription(
    userId: string,
    dto: UpdateUserSubscriptionDto,
    actor: { adminId?: string; ip?: string; userAgent?: string; correlationId?: string },
  ) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    const before = {
      subscriptionTier: user.subscriptionTier ?? null,
      subscriptionExpiresAt: user.subscriptionExpiresAt ?? null,
    };
    if (dto.subscriptionTier !== undefined) {
      (user as { subscriptionTier?: string | null }).subscriptionTier =
        dto.subscriptionTier;
    }
    if (dto.subscriptionExpiresAt !== undefined) {
      (user as { subscriptionExpiresAt?: Date | null }).subscriptionExpiresAt =
        dto.subscriptionExpiresAt === null
          ? null
          : new Date(dto.subscriptionExpiresAt);
    }
    const saved = await this.usersRepo.save(user);
    const after = {
      subscriptionTier: saved.subscriptionTier ?? null,
      subscriptionExpiresAt: saved.subscriptionExpiresAt ?? null,
    };

    await this.auditLogService.write({
      adminId: actor.adminId ?? null,
      action: 'admin.user.subscription.update',
      targetType: 'user',
      targetId: userId,
      before,
      after,
      ip: actor.ip ?? null,
      device: actor.userAgent ?? null,
      reason: dto.reason,
      correlationId: actor.correlationId ?? null,
    });

    return serializePublicUser(saved);
  }
}
