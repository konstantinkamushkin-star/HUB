import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { GroupTripEntity } from './entities/group-trip.entity';
import { GroupTripMemberEntity } from './entities/group-trip-member.entity';
import { CreateGroupTripDto } from './dto/create-group-trip.dto';
import { ChatService } from '../chat/chat.service';
import { FriendsService } from '../friends/friends.service';
import { User } from '../users/entities/user.entity';

export type GroupTripResponse = {
  id: string;
  name: string;
  description: string | null;
  destination: string | null;
  organizerId: string;
  chatId: string;
  participants: string[];
  startDate: string | null;
  endDate: string | null;
  createdAt: string;
  updatedAt: string;
};

@Injectable()
export class GroupTripsService {
  constructor(
    @InjectRepository(GroupTripEntity)
    private readonly tripRepo: Repository<GroupTripEntity>,
    @InjectRepository(GroupTripMemberEntity)
    private readonly memberRepo: Repository<GroupTripMemberEntity>,
    @InjectRepository(User)
    private readonly userRepo: Repository<User>,
    private readonly chatService: ChatService,
    private readonly friendsService: FriendsService,
  ) {}

  private async toResponse(trip: GroupTripEntity): Promise<GroupTripResponse> {
    const members = await this.memberRepo.find({ where: { tripId: trip.id } });
    return {
      id: trip.id,
      name: trip.name,
      description: trip.description,
      destination: trip.destination,
      organizerId: trip.organizerId,
      chatId: trip.chatConversationId!,
      participants: members.map((m) => m.userId),
      startDate: trip.startDate,
      endDate: trip.endDate,
      createdAt: trip.createdAt.toISOString(),
      updatedAt: trip.updatedAt.toISOString(),
    };
  }

  private async assertMember(tripId: string, userId: string): Promise<GroupTripEntity> {
    const trip = await this.tripRepo.findOne({ where: { id: tripId } });
    if (!trip) {
      throw new NotFoundException('Group trip not found');
    }
    const member = await this.memberRepo.findOne({
      where: { tripId, userId },
    });
    if (!member) {
      throw new ForbiddenException('Not a member of this trip');
    }
    return trip;
  }

  async listForUser(userId: string): Promise<GroupTripResponse[]> {
    const memberships = await this.memberRepo.find({ where: { userId } });
    if (!memberships.length) {
      return [];
    }
    const tripIds = memberships.map((m) => m.tripId);
    const trips = await this.tripRepo.find({
      where: { id: In(tripIds) },
      order: { startDate: 'DESC', updatedAt: 'DESC' },
    });
    const out: GroupTripResponse[] = [];
    for (const t of trips) {
      out.push(await this.toResponse(t));
    }
    return out;
  }

  async getById(tripId: string, userId: string): Promise<GroupTripResponse> {
    await this.assertMember(tripId, userId);
    const trip = await this.tripRepo.findOneOrFail({ where: { id: tripId } });
    return this.toResponse(trip);
  }

  async create(
    organizerId: string,
    dto: CreateGroupTripDto,
  ): Promise<GroupTripResponse> {
    const memberIds = [...new Set(dto.memberUserIds ?? [])].filter(
      (id) => id !== organizerId,
    );

    const friends = await this.friendsService.listFriendUserIds(organizerId);
    for (const mid of memberIds) {
      if (!friends.includes(mid)) {
        throw new BadRequestException(
          `User ${mid} is not in your friends list`,
        );
      }
    }

    const trip = this.tripRepo.create({
      name: dto.name.trim(),
      description: dto.description?.trim() || null,
      destination: dto.destination?.trim() || null,
      organizerId,
      startDate: dto.startDate ?? null,
      endDate: dto.endDate ?? null,
      chatConversationId: null,
    });
    const savedTrip = await this.tripRepo.save(trip);

    const allUserIds = [organizerId, ...memberIds];
    const chatId = await this.chatService.createTripGroupChat(
      savedTrip.id,
      savedTrip.name,
      allUserIds,
    );
    savedTrip.chatConversationId = chatId;
    await this.tripRepo.save(savedTrip);

    const memberRows: Partial<GroupTripMemberEntity>[] = [
      { tripId: savedTrip.id, userId: organizerId, role: 'organizer' },
      ...memberIds.map((uid) => ({
        tripId: savedTrip.id,
        userId: uid,
        role: 'member',
      })),
    ];
    await this.memberRepo.save(
      memberRows.map((r) => this.memberRepo.create(r)),
    );

    return this.toResponse(savedTrip);
  }

  async addMember(
    tripId: string,
    actorId: string,
    newUserId: string,
  ): Promise<GroupTripResponse> {
    const trip = await this.assertMember(tripId, actorId);
    if (trip.organizerId !== actorId) {
      throw new ForbiddenException('Only the organizer can invite members');
    }
    if (newUserId === actorId) {
      throw new BadRequestException('Already a member');
    }

    const friends = await this.friendsService.listFriendUserIds(actorId);
    if (!friends.includes(newUserId)) {
      throw new BadRequestException('Can only invite friends');
    }

    const existing = await this.memberRepo.findOne({
      where: { tripId, userId: newUserId },
    });
    if (existing) {
      return this.toResponse(trip);
    }

    await this.memberRepo.save(
      this.memberRepo.create({
        tripId,
        userId: newUserId,
        role: 'member',
      }),
    );

    if (trip.chatConversationId) {
      await this.chatService.addUserToTripGroupChat(
        trip.chatConversationId,
        newUserId,
      );
    }

    const fresh = await this.tripRepo.findOneOrFail({ where: { id: tripId } });
    return this.toResponse(fresh);
  }
}
