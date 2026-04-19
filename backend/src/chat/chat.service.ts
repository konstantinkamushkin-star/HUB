import {
  HttpException,
  HttpStatus,
  Injectable,
  BadRequestException,
  ForbiddenException,
  Logger,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { FriendsService } from '../friends/friends.service';
import { NotificationsService } from '../notifications/notifications.service';
import { PushService } from '../push/push.service';
import { ChatConversation } from './entities/chat-conversation.entity';
import { ChatParticipant } from './entities/chat-participant.entity';
import { ChatMessageEntity } from './entities/chat-message.entity';
import { ChatPeerTypeDto, OpenChatDto } from './dto/open-chat.dto';
import { SendChatMessageDto } from './dto/send-chat-message.dto';

function pgDriverMeta(err: unknown): {
  code?: string;
  detail?: string;
  message?: string;
} {
  if (!err || typeof err !== 'object') {
    return {};
  }
  const d = (err as { driverError?: { code?: string; detail?: string; message?: string } })
    .driverError;
  return d ?? {};
}

function senderDisplayName(
  senderType: string,
  senderId: string,
  userCache: Map<string, User>,
  centerCache: Map<string, DiveCenterEntity>,
  shopCache: Map<string, ShopEntity>,
): string {
  if (senderType === 'user') {
    const u = userCache.get(senderId);
    if (u) {
      return `${u.firstName} ${u.lastName}`.trim() || u.email;
    }
    return 'User';
  }
  if (senderType === 'dive_center') {
    return centerCache.get(senderId)?.name ?? 'Dive center';
  }
  if (senderType === 'shop') {
    return shopCache.get(senderId)?.name ?? 'Shop';
  }
  return 'Unknown';
}

@Injectable()
export class ChatService {
  private readonly logger = new Logger(ChatService.name);

  constructor(
    @InjectRepository(ChatConversation)
    private readonly convRepository: Repository<ChatConversation>,
    @InjectRepository(ChatParticipant)
    private readonly participantRepository: Repository<ChatParticipant>,
    @InjectRepository(ChatMessageEntity)
    private readonly messageRepository: Repository<ChatMessageEntity>,
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private readonly diveCenterRepository: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private readonly shopRepository: Repository<ShopEntity>,
    private readonly friendsService: FriendsService,
    private readonly eventEmitter: EventEmitter2,
    private readonly pushService: PushService,
    private readonly notificationsService: NotificationsService,
    @InjectDataSource()
    private readonly dataSource: DataSource,
  ) {}

  private canonicalKey(
    peerType: ChatPeerTypeDto,
    userId: string,
    peerId: string,
  ): string {
    if (peerType === ChatPeerTypeDto.user) {
      const [a, b] = [userId, peerId].sort();
      return `uu:${a}:${b}`;
    }
    if (peerType === ChatPeerTypeDto.dive_center) {
      return `udc:${userId}:${peerId}`;
    }
    return `us:${userId}:${peerId}`;
  }

  private async assertParticipant(
    conversationId: string,
    userId: string,
  ): Promise<void> {
    const row = await this.participantRepository.findOne({
      where: {
        conversationId,
        participantType: 'user',
        participantId: userId,
      },
    });
    if (!row) {
      throw new ForbiddenException('Not a participant in this conversation');
    }
  }

  private async resolvePeerTitle(
    peerType: ChatPeerTypeDto,
    peerId: string,
  ): Promise<string> {
    if (peerType === ChatPeerTypeDto.user) {
      const u = await this.userRepository.findOne({ where: { id: peerId } });
      if (!u) {
        throw new NotFoundException('User not found');
      }
      return `${u.firstName} ${u.lastName}`.trim() || u.email;
    }
    if (peerType === ChatPeerTypeDto.dive_center) {
      const c = await this.diveCenterRepository.findOne({ where: { id: peerId } });
      if (!c) {
        throw new NotFoundException('Dive center not found');
      }
      return c.name;
    }
    const s = await this.shopRepository.findOne({ where: { id: peerId } });
    if (!s) {
      throw new NotFoundException('Shop not found');
    }
    return s.name;
  }

  /** Maps DB/runtime errors to HttpException so Nest does not return generic "Internal server error". */
  private failOpenConversation(err: unknown, logLabel: string): never {
    if (err instanceof HttpException) {
      throw err;
    }
    const { code, detail, message: drvMsg } = pgDriverMeta(err);
    const text =
      detail ||
      drvMsg ||
      (err instanceof Error ? err.message : String(err));
    this.logger.error(
      `${logLabel} code=${code}: ${text}`,
      err instanceof Error ? err.stack : undefined,
    );
    if (code === '42P01') {
      throw new ServiceUnavailableException(
        'Chat tables missing: apply backend/migrations/010_create_chat.sql (and 011_chat_attachments_push_devices.sql if needed).',
      );
    }
    if (code === '42703') {
      throw new ServiceUnavailableException(
        'Chat schema incomplete: apply backend/migrations/011_chat_attachments_push_devices.sql',
      );
    }
    throw new HttpException(text, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  async openConversation(userId: string, dto: OpenChatDto) {
    if (dto.peerType === ChatPeerTypeDto.user) {
      if (dto.peerId === userId) {
        throw new BadRequestException('Cannot open a chat with yourself');
      }
      const friends = await this.friendsService.listFriendUserIds(userId);
      if (!friends.includes(dto.peerId)) {
        throw new ForbiddenException('You can only message accepted friends');
      }
    }

    await this.resolvePeerTitle(dto.peerType, dto.peerId);

    try {
      const key = this.canonicalKey(dto.peerType, userId, dto.peerId);
      let conv = await this.convRepository.findOne({ where: { canonicalKey: key } });

      if (!conv) {
        try {
          const kind =
            dto.peerType === ChatPeerTypeDto.user
              ? 'USER_USER'
              : dto.peerType === ChatPeerTypeDto.dive_center
                ? 'USER_DIVE_CENTER'
                : 'USER_SHOP';

          conv = this.convRepository.create({
            kind,
            canonicalKey: key,
            diveCenterId:
              dto.peerType === ChatPeerTypeDto.dive_center ? dto.peerId : null,
            shopId: dto.peerType === ChatPeerTypeDto.shop ? dto.peerId : null,
            bookingId: null,
          });
          await this.convRepository.save(conv);

          const rows: Partial<ChatParticipant>[] = [
            {
              conversationId: conv.id,
              participantType: 'user',
              participantId: userId,
              lastReadAt: new Date(),
            },
          ];

          if (dto.peerType === ChatPeerTypeDto.user) {
            rows.push({
              conversationId: conv.id,
              participantType: 'user',
              participantId: dto.peerId,
              lastReadAt: null,
            });
          } else if (dto.peerType === ChatPeerTypeDto.dive_center) {
            rows.push({
              conversationId: conv.id,
              participantType: 'dive_center',
              participantId: dto.peerId,
              lastReadAt: null,
            });
          } else {
            rows.push({
              conversationId: conv.id,
              participantType: 'shop',
              participantId: dto.peerId,
              lastReadAt: null,
            });
          }

          await this.participantRepository.save(
            rows.map((r) => this.participantRepository.create(r)),
          );
        } catch (err: unknown) {
          if (err instanceof HttpException) {
            throw err;
          }
          const { code, detail, message: drvMsg } = pgDriverMeta(err);
          const text =
            detail ||
            drvMsg ||
            (err instanceof Error ? err.message : String(err));
          this.logger.warn(
            `openConversation insert failed code=${code} msg=${text}`,
          );
          if (code === '23505') {
            conv = await this.convRepository.findOne({
              where: { canonicalKey: key },
            });
          } else if (code === '42P01' || code === '42703') {
            this.failOpenConversation(err, 'openConversation insert');
          }
          if (!conv) {
            this.failOpenConversation(err, 'openConversation insert');
          }
        }
      }

      if (!conv) {
        throw new ServiceUnavailableException(
          'Could not open conversation (race or inconsistent state). Retry.',
        );
      }

      return await this.serializeConversation(conv.id, userId);
    } catch (e: unknown) {
      this.failOpenConversation(e, 'openConversation');
    }
  }

  private async unreadCountFor(
    conversationId: string,
    userId: string,
  ): Promise<number> {
    const me = await this.participantRepository.findOne({
      where: {
        conversationId,
        participantType: 'user',
        participantId: userId,
      },
    });
    const since = me?.lastReadAt ?? new Date(0);
    return this.messageRepository
      .createQueryBuilder('m')
      .where('m.conversationId = :cid', { cid: conversationId })
      .andWhere('m.createdAt > :since', { since })
      .andWhere('NOT (m.senderType = :ut AND m.senderId = :uid)', {
        ut: 'user',
        uid: userId,
      })
      .getCount();
  }

  private serializeMessage(
    m: ChatMessageEntity,
    userCache: Map<string, User>,
    centerCache: Map<string, DiveCenterEntity>,
    shopCache: Map<string, ShopEntity>,
  ) {
    const senderName = senderDisplayName(
      m.senderType,
      m.senderId,
      userCache,
      centerCache,
      shopCache,
    );
    return {
      id: m.id,
      conversationId: m.conversationId,
      senderId: m.senderId,
      senderName,
      content: m.content,
      messageType: m.messageType,
      attachments: m.attachments ?? null,
      location: null,
      isRead: true,
      createdAt: m.createdAt,
    };
  }

  private async pushRecipientUserIds(
    conversationId: string,
    excludeUserId: string,
  ): Promise<string[]> {
    const parts = await this.participantRepository.find({
      where: { conversationId },
    });
    return parts
      .filter((p) => p.participantType === 'user' && p.participantId !== excludeUserId)
      .map((p) => p.participantId);
  }

  async serializeConversation(conversationId: string, viewerId: string) {
    await this.assertParticipant(conversationId, viewerId);

    const conv = await this.convRepository.findOne({
      where: { id: conversationId },
    });
    if (!conv) {
      throw new NotFoundException('Conversation not found');
    }

    const parts = await this.participantRepository.find({
      where: { conversationId },
    });

    const peerIds: string[] = [];
    let peerDisplayName = 'Chat';

    for (const p of parts) {
      if (p.participantType === 'user' && p.participantId !== viewerId) {
        peerIds.push(p.participantId);
        const u = await this.userRepository.findOne({
          where: { id: p.participantId },
        });
        if (u) {
          peerDisplayName = `${u.firstName} ${u.lastName}`.trim() || u.email;
        }
      } else if (p.participantType === 'dive_center') {
        peerIds.push(p.participantId);
        const c = await this.diveCenterRepository.findOne({
          where: { id: p.participantId },
        });
        if (c) {
          peerDisplayName = c.name;
        }
      } else if (p.participantType === 'shop') {
        peerIds.push(p.participantId);
        const s = await this.shopRepository.findOne({
          where: { id: p.participantId },
        });
        if (s) {
          peerDisplayName = s.name;
        }
      }
    }

    const last = await this.messageRepository.findOne({
      where: { conversationId },
      order: { createdAt: 'DESC' },
    });

    const userCache = new Map<string, User>();
    const centerCache = new Map<string, DiveCenterEntity>();
    const shopCache = new Map<string, ShopEntity>();

    let lastMessage = null;
    if (last) {
      if (last.senderType === 'user') {
        const u = await this.userRepository.findOne({
          where: { id: last.senderId },
        });
        if (u) {
          userCache.set(u.id, u);
        }
      } else if (last.senderType === 'dive_center') {
        const c = await this.diveCenterRepository.findOne({
          where: { id: last.senderId },
        });
        if (c) {
          centerCache.set(c.id, c);
        }
      } else if (last.senderType === 'shop') {
        const s = await this.shopRepository.findOne({
          where: { id: last.senderId },
        });
        if (s) {
          shopCache.set(s.id, s);
        }
      }
      lastMessage = this.serializeMessage(
        last,
        userCache,
        centerCache,
        shopCache,
      );
    }

    const unreadCount = await this.unreadCountFor(conversationId, viewerId);

    return {
      id: conv.id,
      participants: peerIds,
      peerDisplayName,
      diveCenterId: conv.diveCenterId,
      shopId: conv.shopId,
      bookingId: conv.bookingId,
      lastMessage,
      unreadCount,
      createdAt: conv.createdAt,
      updatedAt: conv.updatedAt,
    };
  }

  async listConversations(userId: string) {
    const rows = await this.participantRepository
      .createQueryBuilder('p')
      .innerJoinAndSelect('p.conversation', 'c')
      .where('p.participantType = :t', { t: 'user' })
      .andWhere('p.participantId = :uid', { uid: userId })
      .orderBy('c.updatedAt', 'DESC')
      .getMany();

    const out = [];
    for (const row of rows) {
      out.push(await this.serializeConversation(row.conversationId, userId));
    }
    return out;
  }

  async listMessages(
    userId: string,
    conversationId: string,
    options?: {
      beforeMessageId?: string;
      limit?: number;
      markRead?: boolean;
    },
  ) {
    await this.assertParticipant(conversationId, userId);

    const limit = Math.min(Math.max(options?.limit ?? 40, 1), 100);
    const markRead = options?.markRead ?? options?.beforeMessageId == null;

    if (markRead) {
      await this.participantRepository.update(
        {
          conversationId,
          participantType: 'user',
          participantId: userId,
        },
        { lastReadAt: new Date() },
      );
    }

    const qb = this.messageRepository
      .createQueryBuilder('m')
      .where('m.conversationId = :cid', { cid: conversationId });

    if (options?.beforeMessageId) {
      const anchor = await this.messageRepository.findOne({
        where: { id: options.beforeMessageId, conversationId },
      });
      if (!anchor) {
        throw new NotFoundException('Message not found');
      }
      qb.andWhere(
        '(m.createdAt < :ct OR (m.createdAt = :ct AND m.id < :mid))',
        { ct: anchor.createdAt, mid: anchor.id },
      );
    }

    qb.orderBy('m.createdAt', 'DESC')
      .addOrderBy('m.id', 'DESC')
      .take(limit + 1);

    const rows = await qb.getMany();
    const hasMore = rows.length > limit;
    const page = hasMore ? rows.slice(0, limit) : rows;
    page.reverse();

    const userCache = new Map<string, User>();
    const centerCache = new Map<string, DiveCenterEntity>();
    const shopCache = new Map<string, ShopEntity>();

    const userIds = new Set(
      page.filter((m) => m.senderType === 'user').map((m) => m.senderId),
    );
    const centerIds = new Set(
      page.filter((m) => m.senderType === 'dive_center').map((m) => m.senderId),
    );
    const shopIds = new Set(
      page.filter((m) => m.senderType === 'shop').map((m) => m.senderId),
    );

    if (userIds.size) {
      const users = await this.userRepository.find({
        where: { id: In([...userIds]) },
      });
      for (const u of users) {
        userCache.set(u.id, u);
      }
    }
    if (centerIds.size) {
      const centers = await this.diveCenterRepository.find({
        where: { id: In([...centerIds]) },
      });
      for (const c of centers) {
        centerCache.set(c.id, c);
      }
    }
    if (shopIds.size) {
      const shops = await this.shopRepository.find({
        where: { id: In([...shopIds]) },
      });
      for (const s of shops) {
        shopCache.set(s.id, s);
      }
    }

    const messages = page.map((m) =>
      this.serializeMessage(m, userCache, centerCache, shopCache),
    );
    const nextBefore =
      hasMore && page.length ? (page[0] as ChatMessageEntity).id : null;
    return { messages, hasMore, nextBefore };
  }

  async sendMessage(userId: string, dto: SendChatMessageDto) {
    await this.assertParticipant(dto.conversationId, userId);

    const type = dto.messageType ?? 'text';
    let content = (dto.content ?? '').trim();
    const attachments = dto.attachments ?? null;

    if (type === 'photo') {
      if (!attachments?.length) {
        throw new BadRequestException('Photo messages require attachments');
      }
      if (!content) {
        content = ' ';
      }
    } else if (type === 'text') {
      if (!content) {
        throw new BadRequestException('Empty message');
      }
    } else {
      throw new BadRequestException('Unsupported message type');
    }

    const row = this.messageRepository.create({
      conversationId: dto.conversationId,
      senderType: 'user',
      senderId: userId,
      content,
      messageType: type,
      attachments,
    });

    try {
      const saved = await this.messageRepository.save(row);

      const u = await this.userRepository.findOne({ where: { id: userId } });
      const userCache = new Map<string, User>();
      if (u) {
        userCache.set(u.id, u);
      }
      const serialized = this.serializeMessage(
        saved,
        userCache,
        new Map(),
        new Map(),
      );

      try {
        this.eventEmitter.emit('chat.message', {
          conversationId: dto.conversationId,
          message: serialized,
        });
      } catch (emitErr) {
        this.logger.warn(
          `chat.message emit failed: ${emitErr instanceof Error ? emitErr.message : String(emitErr)}`,
        );
      }

      try {
        const recipients = await this.pushRecipientUserIds(
          dto.conversationId,
          userId,
        );
        const preview =
          type === 'photo'
            ? '📷 Photo'
            : (serialized.content ?? '').slice(0, 120);
        try {
          await this.pushService.notifyUsers(
            recipients,
            serialized.senderName,
            preview,
          );
        } catch (pushErr) {
          this.logger.warn(
            `Chat push notify failed (message was saved): ${pushErr instanceof Error ? pushErr.message : String(pushErr)}`,
          );
        }
        for (const rid of recipients) {
          await this.notificationsService.createForUser(rid, {
            type: 'message',
            title: serialized.senderName,
            message: preview,
            icon: 'message',
            actionUrl: `divehub://chat?conversationId=${dto.conversationId}`,
          });
        }
      } catch (sideErr) {
        this.logger.warn(
          `Chat post-save notify failed (message was saved): ${sideErr instanceof Error ? sideErr.message : String(sideErr)}`,
        );
      }

      return serialized;
    } catch (e: unknown) {
      if (e instanceof HttpException) {
        throw e;
      }
      const { code, detail, message: drvMsg } = pgDriverMeta(e);
      const text =
        detail ||
        drvMsg ||
        (e instanceof Error ? e.message : String(e));
      this.logger.error(
        `sendMessage failed code=${code}: ${text}`,
        e instanceof Error ? e.stack : undefined,
      );
      if (
        code === '22021' ||
        code === '22P05' ||
        /encoding|UTF8|character/i.test(text)
      ) {
        throw new BadRequestException(
          'Database text encoding error. Recreate the database with UTF-8 (e.g. `createdb -E UTF8 divehub`) or set encoding=UTF8 on the server.',
        );
      }
      throw new HttpException(text, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private async resolveSupportAdminUserId(): Promise<string | null> {
    const envId = process.env.DIVE_SITE_SUPPORT_ADMIN_USER_ID?.trim();
    if (envId && /^[0-9a-f-]{36}$/i.test(envId)) {
      const u = await this.userRepository.findOne({ where: { id: envId } });
      if (u) return u.id;
    }
    const row = await this.userRepository
      .createQueryBuilder('u')
      .where('u.role IN (:...roles)', { roles: ['SUPER_ADMIN', 'ADMIN'] })
      .orderBy('u.role', 'DESC')
      .take(1)
      .getOne();
    return row?.id ?? null;
  }

  contributionConversationCanonicalKey(contributionId: string): string {
    return `dsc:${contributionId}`;
  }

  async findContributionConversationId(
    contributionId: string,
  ): Promise<string | null> {
    const key = this.contributionConversationCanonicalKey(contributionId);
    const conv = await this.convRepository.findOne({ where: { canonicalKey: key } });
    return conv?.id ?? null;
  }

  /**
   * Чат пользователь ↔ админ по заявке на дайв-сайт (без «друзей»).
   */
  async openContributionSupportChat(
    userId: string,
    contributionId: string,
  ) {
    const rows: { submitter_user_id: string }[] = await this.dataSource.query(
      `SELECT submitter_user_id FROM dive_site_contributions WHERE id = $1`,
      [contributionId],
    );
    if (!rows.length) {
      throw new NotFoundException('Contribution not found');
    }
    if (rows[0].submitter_user_id !== userId) {
      throw new ForbiddenException('Not your contribution');
    }
    const supportId = await this.resolveSupportAdminUserId();
    if (!supportId) {
      throw new ServiceUnavailableException(
        'No support admin user (set DIVE_SITE_SUPPORT_ADMIN_USER_ID or ensure an ADMIN/SUPER_ADMIN exists).',
      );
    }
    await this.ensureContributionSupportThread(userId, contributionId, supportId);
    const cid = await this.findContributionConversationId(contributionId);
    if (!cid) {
      throw new ServiceUnavailableException('Could not open contribution chat.');
    }
    return this.serializeConversation(cid, userId);
  }

  async ensureContributionSupportThread(
    submitterUserId: string,
    contributionId: string,
    supportUserId?: string,
  ): Promise<string | null> {
    const supportId = supportUserId ?? (await this.resolveSupportAdminUserId());
    if (!supportId) {
      this.logger.warn('ensureContributionSupportThread: no support admin');
      return null;
    }
    const key = this.contributionConversationCanonicalKey(contributionId);
    let conv = await this.convRepository.findOne({ where: { canonicalKey: key } });
    if (conv) {
      return conv.id;
    }
    try {
      conv = this.convRepository.create({
        kind: 'CONTRIBUTION_SUPPORT',
        canonicalKey: key,
        diveCenterId: null,
        shopId: null,
        bookingId: null,
      });
      await this.convRepository.save(conv);
      await this.participantRepository.save([
        this.participantRepository.create({
          conversationId: conv.id,
          participantType: 'user',
          participantId: submitterUserId,
          lastReadAt: new Date(),
        }),
        this.participantRepository.create({
          conversationId: conv.id,
          participantType: 'user',
          participantId: supportId,
          lastReadAt: null,
        }),
      ]);
      const welcome =
        'Здравствуйте! Этот чат для уточнений по вашей заявке на дайв-сайт. Напишите, если нужна помощь.';
      const msg = this.messageRepository.create({
        conversationId: conv.id,
        senderType: 'user',
        senderId: supportId,
        content: welcome,
        messageType: 'text',
        attachments: null,
      });
      await this.messageRepository.save(msg);
      await this.deliverChatPushAndInboxForMessage(conv.id, supportId, welcome);
    } catch (err: unknown) {
      const { code } = pgDriverMeta(err);
      if (code === '23505') {
        conv = await this.convRepository.findOne({ where: { canonicalKey: key } });
        return conv?.id ?? null;
      }
      this.logger.warn(
        `ensureContributionSupportThread failed: ${err instanceof Error ? err.message : String(err)}`,
      );
      return null;
    }
    return conv!.id;
  }

  /**
   * Сообщение от модератора о результате рассмотрения — попадает в ленту чата и в пуш как обычное сообщение.
   */
  async postContributionReviewMessage(
    contributionId: string,
    reviewerUserId: string,
    outcome: 'approved' | 'rejected',
    rejectionReason?: string | null,
  ): Promise<boolean> {
    const cid = await this.findContributionConversationId(contributionId);
    if (!cid) {
      return false;
    }
    await this.assertParticipant(cid, reviewerUserId);
    const text =
      outcome === 'approved'
        ? '✅ Ваша заявка по дайв-сайту одобрена.'
        : `❌ Заявка отклонена.${rejectionReason ? ` ${rejectionReason}` : ''}`;
    const row = this.messageRepository.create({
      conversationId: cid,
      senderType: 'user',
      senderId: reviewerUserId,
      content: text,
      messageType: 'text',
      attachments: null,
    });
    const saved = await this.messageRepository.save(row);
    const u = await this.userRepository.findOne({ where: { id: reviewerUserId } });
    const userCache = new Map<string, User>();
    if (u) userCache.set(u.id, u);
    const serialized = this.serializeMessage(
      saved,
      userCache,
      new Map(),
      new Map(),
    );
    try {
      this.eventEmitter.emit('chat.message', {
        conversationId: cid,
        message: serialized,
      });
    } catch (emitErr) {
      this.logger.warn(
        `chat.message emit failed: ${emitErr instanceof Error ? emitErr.message : String(emitErr)}`,
      );
    }
    await this.deliverChatPushAndInboxForMessage(cid, reviewerUserId, text, serialized.senderName);
    return true;
  }

  private async deliverChatPushAndInboxForMessage(
    conversationId: string,
    senderUserId: string,
    preview: string,
    senderNameOverride?: string,
  ): Promise<void> {
    try {
      const recipients = await this.pushRecipientUserIds(
        conversationId,
        senderUserId,
      );
      const u = await this.userRepository.findOne({ where: { id: senderUserId } });
      const title =
        senderNameOverride ??
        (u ? `${u.firstName} ${u.lastName}`.trim() || u.email : 'DiveHub');
      const body = preview.slice(0, 120);
      await this.pushService.notifyUsers(recipients, title, body);
      for (const rid of recipients) {
        await this.notificationsService.createForUser(rid, {
          type: 'message',
          title,
          message: body,
          icon: 'message',
          actionUrl: `divehub://chat?conversationId=${conversationId}`,
        });
      }
    } catch (e) {
      this.logger.warn(
        `deliverChatPushAndInboxForMessage failed: ${e instanceof Error ? e.message : String(e)}`,
      );
    }
  }
}
