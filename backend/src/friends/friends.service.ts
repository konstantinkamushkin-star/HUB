import {
  Injectable,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { Friendship } from './entities/friendship.entity';
import { SendFriendRequestDto } from './dto/send-friend-request.dto';
import { NotificationsService } from '../notifications/notifications.service';

function toPublicUser(user: User): Omit<User, 'password'> {
  const { password: _, ...rest } = user;
  return rest;
}

function userDisplayName(u: User): string {
  const fn = (u.firstName ?? '').trim();
  const ln = (u.lastName ?? '').trim();
  if (fn && ln) return `${fn} ${ln}`;
  if (fn) return fn;
  if (ln) return ln;
  return u.email?.split('@')[0] ?? 'Someone';
}

@Injectable()
export class FriendsService {
  constructor(
    @InjectRepository(Friendship)
    private readonly friendshipRepository: Repository<Friendship>,
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    private readonly notificationsService: NotificationsService,
  ) {}

  /** User IDs of accepted friends (for feed, messaging, etc.). */
  async listFriendUserIds(userId: string): Promise<string[]> {
    const friends = await this.listFriends(userId);
    return friends.map((u) => u.id);
  }

  async listFriends(userId: string): Promise<Omit<User, 'password'>[]> {
    const rows = await this.friendshipRepository
      .createQueryBuilder('f')
      .leftJoinAndSelect('f.requester', 'r')
      .leftJoinAndSelect('f.addressee', 'a')
      .where('f.status = :status', { status: 'accepted' })
      .andWhere('(f.requesterId = :uid OR f.addresseeId = :uid)', { uid: userId })
      .getMany();

    return rows.map((f) =>
      toPublicUser(f.requesterId === userId ? f.addressee : f.requester),
    );
  }

  async sendRequest(
    userId: string,
    dto: SendFriendRequestDto,
  ): Promise<void> {
    const targetId = dto.userId;
    if (targetId === userId) {
      throw new BadRequestException('Cannot send a friend request to yourself');
    }

    const target = await this.userRepository.findOne({
      where: { id: targetId },
    });
    if (!target) {
      throw new NotFoundException('User not found');
    }

    const existing = await this.friendshipRepository.findOne({
      where: [
        { requesterId: userId, addresseeId: targetId },
        { requesterId: targetId, addresseeId: userId },
      ],
    });

    if (existing) {
      if (existing.status === 'accepted') {
        throw new BadRequestException('Already friends');
      }
      throw new BadRequestException('Friend request already pending');
    }

    const row = this.friendshipRepository.create({
      requesterId: userId,
      addresseeId: targetId,
      status: 'pending',
    });
    await this.friendshipRepository.save(row);

    const requester = await this.userRepository.findOne({ where: { id: userId } });
    if (requester) {
      await this.notificationsService.createForUser(targetId, {
        type: 'friend_request',
        title: 'Friend request',
        message: `${userDisplayName(requester)} sent you a friend request`,
        icon: 'person.badge.plus',
        actionUrl: 'divehub://social',
      });
    }
  }

  async listSent(userId: string) {
    const rows = await this.friendshipRepository.find({
      where: { requesterId: userId, status: 'pending' },
      relations: ['addressee'],
      order: { createdAt: 'DESC' },
    });
    return rows.map((f) => ({
      id: f.id,
      user: toPublicUser(f.addressee),
      createdAt: f.createdAt,
    }));
  }

  async listReceived(userId: string) {
    const rows = await this.friendshipRepository.find({
      where: { addresseeId: userId, status: 'pending' },
      relations: ['requester'],
      order: { createdAt: 'DESC' },
    });
    return rows.map((f) => ({
      id: f.id,
      user: toPublicUser(f.requester),
      createdAt: f.createdAt,
    }));
  }

  async accept(userId: string, requesterId: string): Promise<void> {
    const row = await this.friendshipRepository.findOne({
      where: {
        requesterId,
        addresseeId: userId,
        status: 'pending',
      },
    });
    if (!row) {
      throw new NotFoundException('Friend request not found');
    }
    // update(), not save(row): without loaded relations TypeORM save can NULL FKs → 500.
    const upd = await this.friendshipRepository.update(
      { id: row.id, addresseeId: userId, status: 'pending' },
      { status: 'accepted', updatedAt: new Date() },
    );
    if (!upd.affected) {
      throw new NotFoundException('Friend request not found');
    }

    const acceptor = await this.userRepository.findOne({ where: { id: userId } });
    if (acceptor) {
      await this.notificationsService.createForUser(requesterId, {
        type: 'friend_request',
        title: 'Friend request accepted',
        message: `${userDisplayName(acceptor)} accepted your friend request`,
        icon: 'checkmark.circle',
        actionUrl: 'divehub://social',
      });
    }
  }

  async decline(userId: string, friendshipId: string): Promise<void> {
    const row = await this.friendshipRepository.findOne({
      where: { id: friendshipId },
    });
    if (
      !row ||
      row.addresseeId !== userId ||
      row.status !== 'pending'
    ) {
      throw new NotFoundException('Friend request not found');
    }
    await this.friendshipRepository.remove(row);
  }
}
