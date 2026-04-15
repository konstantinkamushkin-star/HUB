import { BadRequestException, Injectable, NotFoundException, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { User } from './entities/user.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveLogModerationStatus, FeedPostStatus, UserAccountStatus } from '../common/statuses';
import { AuditLogService } from '../admin/audit-log.service';
import { DeleteMyAccountDto } from './dto/delete-my-account.dto';
import { UserPushDevice } from '../push/entities/user-push-device.entity';

type PublicUserProfile = {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  avatarUrl: string | null;
  role: string;
  bio: string | null;
  countryCode: string | null;
  totalDives: number;
  createdAt: Date;
  updatedAt: Date;
};

function maskEmail(email: string): string {
  const normalized = email.trim().toLowerCase();
  const [local, domain] = normalized.split('@');
  if (!local || !domain) {
    return 'hidden@users.divehub';
  }
  if (local.length <= 2) {
    return `${local[0] ?? '*'}*@${domain}`;
  }
  return `${local[0]}***${local[local.length - 1]}@${domain}`;
}

function toPublicUser(user: User): PublicUserProfile {
  return {
    id: user.id,
    email: maskEmail(user.email),
    firstName: user.firstName ?? null,
    lastName: user.lastName ?? null,
    avatarUrl: user.avatarUrl ?? null,
    role: user.role,
    bio: user.bio ?? null,
    countryCode: user.countryCode ?? null,
    totalDives: user.totalDives ?? 0,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  };
}

function toExportableUser(
  user: User,
): Omit<User, 'password' | 'adminTotpSecret' | 'passwordResetCode' | 'passwordResetExpires'> {
  const {
    password: _p,
    adminTotpSecret: _totp,
    passwordResetCode: _prc,
    passwordResetExpires: _pre,
    ...rest
  } = user;
  return rest;
}

@Injectable()
export class UsersService {
  private static readonly DELETE_REAUTH_MAX_AGE_MS = 15 * 60 * 1000;

  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    @InjectRepository(FeedPost)
    private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(FeedPostComment)
    private readonly commentsRepo: Repository<FeedPostComment>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    @InjectRepository(UserPushDevice)
    private readonly pushDevicesRepo: Repository<UserPushDevice>,
    private readonly auditLogService: AuditLogService,
  ) {}

  private async safeWriteAudit(input: Parameters<AuditLogService['write']>[0]) {
    try {
      await this.auditLogService.write(input);
    } catch {
      // GDPR action should not fail due to audit storage issues.
    }
  }

  async search(currentUserId: string, query: string) {
    const q = query?.trim() ?? '';
    if (q.length < 2) {
      return [];
    }

    const pattern = `%${q}%`;

    const rows = await this.userRepository
      .createQueryBuilder('u')
      .where('u.id != :me', { me: currentUserId })
      .andWhere('u.accountStatus = :active', { active: UserAccountStatus.ACTIVE })
      .andWhere(
        '(LOWER(u.email) LIKE LOWER(:p) OR LOWER(u."firstName") LIKE LOWER(:p) OR LOWER(u."lastName") LIKE LOWER(:p) OR LOWER(CONCAT(u."firstName", \' \', u."lastName")) LIKE LOWER(:p))',
        { p: pattern },
      )
      .take(50)
      .getMany();

    return rows.map(toPublicUser);
  }

  async findById(id: string) {
    const user = await this.userRepository.findOne({
      where: { id, accountStatus: UserAccountStatus.ACTIVE },
    });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    return toPublicUser(user);
  }

  async exportMyData(
    userId: string,
    actor?: { ip?: string | null; userAgent?: string | null; correlationId?: string | null },
  ) {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }

    const [posts, comments, diveLogs] = await Promise.all([
      this.postsRepo.find({
        where: { userId },
        order: { createdAt: 'DESC' },
      }),
      this.commentsRepo.find({
        where: { userId },
        order: { createdAt: 'DESC' },
      }),
      this.logsRepo.find({
        where: { userId },
        order: { date: 'DESC' },
      }),
    ]);

    const payload = {
      generatedAt: new Date().toISOString(),
      user: toExportableUser(user),
      stats: {
        posts: posts.length,
        comments: comments.length,
        diveLogs: diveLogs.length,
      },
      data: {
        posts,
        comments,
        diveLogs,
      },
    };

    await this.safeWriteAudit({
      adminId: null,
      action: 'user.gdpr.export_data',
      targetType: 'user',
      targetId: userId,
      after: { stats: payload.stats },
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });

    return payload;
  }

  async deleteMyAccount(
    userId: string,
    dto: DeleteMyAccountDto,
    actor?: {
      deleteConfirmHeader?: string | null;
      ip?: string | null;
      userAgent?: string | null;
      correlationId?: string | null;
    },
  ) {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }

    const confirmation = dto.confirmation?.trim().toUpperCase();
    if (confirmation !== 'DELETE') {
      throw new BadRequestException('Deletion confirmation must be "DELETE"');
    }
    if ((actor?.deleteConfirmHeader ?? '').trim().toLowerCase() !== 'true') {
      throw new BadRequestException('x-account-delete-confirm header must be "true"');
    }

    const currentPassword = dto.currentPassword?.trim() ?? '';
    let hasStrongReauth = false;
    if (currentPassword) {
      hasStrongReauth = await bcrypt.compare(currentPassword, user.password);
    }
    const hasRecentSession =
      user.lastLogin instanceof Date &&
      Date.now() - user.lastLogin.getTime() <= UsersService.DELETE_REAUTH_MAX_AGE_MS;
    if (!hasStrongReauth && !hasRecentSession) {
      throw new UnauthorizedException(
        'Re-auth required: provide currentPassword or sign in again and retry within 15 minutes',
      );
    }

    if (user.accountStatus === UserAccountStatus.DELETED) {
      return { ok: true, alreadyDeleted: true };
    }

    const deletedAt = new Date();
    const deletedAtStamp = deletedAt.getTime();

    user.accountStatus = UserAccountStatus.DELETED;
    user.deletedAt = deletedAt;
    user.email = `deleted+${deletedAtStamp}.${user.id}@divehub.local`;
    user.firstName = 'Deleted';
    user.lastName = 'User';
    user.avatarUrl = null;
    user.phone = null;
    user.dateOfBirth = null;
    user.bio = null;
    user.diverProfile = null;
    user.emailVerified = false;
    user.phoneVerified = false;
    user.passwordResetCode = null;
    user.passwordResetExpires = null;
    user.appleSub = null;
    user.googleSub = null;
    user.password = await bcrypt.hash(`deleted:${user.id}:${deletedAtStamp}`, 10);

    await this.userRepository.save(user);

    await Promise.all([
      this.postsRepo
        .createQueryBuilder()
        .update(FeedPost)
        .set({
          content: null,
          photos: [],
          moderationStatus: FeedPostStatus.REMOVED,
          deletedAt,
        })
        .where('userId = :userId', { userId })
        .execute(),
      this.commentsRepo
        .createQueryBuilder()
        .update(FeedPostComment)
        .set({
          content: '[deleted]',
          moderationStatus: FeedPostStatus.REMOVED,
          deletedAt,
        })
        .where('userId = :userId', { userId })
        .execute(),
      this.logsRepo
        .createQueryBuilder()
        .update(DiveLogEntity)
        .set({
          notes: null,
          photoUrls: [],
          videoUrls: [],
          fishSpecies: [],
          moderationStatus: DiveLogModerationStatus.REMOVED,
          deletedAt,
          isPublished: false,
        })
        .where('userId = :userId', { userId })
        .execute(),
      this.pushDevicesRepo
        .createQueryBuilder()
        .delete()
        .from(UserPushDevice)
        .where('userId = :userId', { userId })
        .execute(),
    ]);

    const payload = {
      ok: true,
      deletedAt: deletedAt.toISOString(),
    };

    await this.safeWriteAudit({
      adminId: null,
      action: 'user.gdpr.delete_account',
      targetType: 'user',
      targetId: userId,
      after: payload,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });

    return payload;
  }
}
