import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { UserLocationEntity } from './entities/user-location.entity';
import { FriendsService } from '../friends/friends.service';
import { blurCoordinates, haversineKm } from '../common/geo.util';
import {
  diverProfileRecord,
  isDiscoverableUser,
} from '../common/diver-profile.util';
import { UserAccountStatus } from '../common/statuses';
import { ReportLocationDto } from './dto/report-location.dto';
import { UpdateLocationSettingsDto } from './dto/update-location-settings.dto';
import { UpdatePrivacySettingsDto } from './dto/update-privacy-settings.dto';

export type FriendLocationDto = {
  userId: string;
  firstName: string | null;
  lastName: string | null;
  avatarUrl: string | null;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  source: string;
  updatedAt: string;
  distanceKm?: number;
};

export type DiscoverUserDto = {
  userId: string;
  firstName: string | null;
  lastName: string | null;
  avatarUrl: string | null;
  latitude: number;
  longitude: number;
  distanceKm: number;
  updatedAt: string;
};

@Injectable()
export class LocationService {
  private readonly lastUploadAt = new Map<string, number>();

  constructor(
    @InjectRepository(UserLocationEntity)
    private readonly locationRepo: Repository<UserLocationEntity>,
    @InjectRepository(User)
    private readonly userRepo: Repository<User>,
    private readonly friendsService: FriendsService,
  ) {}

  async updatePrivacySettings(
    userId: string,
    dto: UpdatePrivacySettingsDto,
  ): Promise<{ ok: true }> {
    const user = await this.userRepo.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (dto.shareLocation !== undefined) {
      user.shareLocation = dto.shareLocation;
      if (!dto.shareLocation) {
        await this.locationRepo.delete({ userId });
        const prev = diverProfileRecord(user) ?? {};
        user.diverProfile = { ...prev, lookingForBuddy: false };
      } else {
        const prev = diverProfileRecord(user) ?? {};
        if (prev.lookingForBuddy !== false) {
          user.diverProfile = { ...prev, lookingForBuddy: true };
        }
      }
    }
    if (dto.publicProfile !== undefined) {
      user.publicProfile = dto.publicProfile;
    }
    if (dto.showInFriendSearch !== undefined) {
      user.showInFriendSearch = dto.showInFriendSearch;
    }
    if (dto.shareLogbook !== undefined) {
      user.shareLogbook = dto.shareLogbook;
    }
    await this.userRepo.save(user);
    return { ok: true };
  }

  async updateLocationSettings(
    userId: string,
    dto: UpdateLocationSettingsDto,
  ): Promise<{ ok: true; shareLocation: boolean; discoverableNearby: boolean }> {
    const user = await this.userRepo.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (dto.shareLocation !== undefined) {
      user.shareLocation = dto.shareLocation;
      if (!dto.shareLocation) {
        await this.locationRepo.delete({ userId });
      }
    }
    if (dto.discoverableNearby !== undefined) {
      const prev = diverProfileRecord(user) ?? {};
      user.diverProfile = {
        ...prev,
        lookingForBuddy: dto.discoverableNearby,
      };
    }
    await this.userRepo.save(user);
    const dp = diverProfileRecord(user);
    return {
      ok: true,
      shareLocation: user.shareLocation,
      discoverableNearby: dp?.lookingForBuddy === true,
    };
  }

  async reportLocation(
    userId: string,
    dto: ReportLocationDto,
  ): Promise<{ ok: true; updatedAt: string }> {
    const now = Date.now();
    const prev = this.lastUploadAt.get(userId) ?? 0;
    if (now - prev < 25_000) {
      const existing = await this.locationRepo.findOne({ where: { userId } });
      if (existing) {
        return { ok: true, updatedAt: existing.updatedAt.toISOString() };
      }
    }
    this.lastUploadAt.set(userId, now);

    const user = await this.userRepo.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (dto.shareLocation === true && !user.shareLocation) {
      user.shareLocation = true;
      const prev = diverProfileRecord(user) ?? {};
      if (prev.lookingForBuddy !== false) {
        user.diverProfile = { ...prev, lookingForBuddy: true };
      }
      await this.userRepo.save(user);
    }
    if (!user.shareLocation) {
      throw new BadRequestException('Location sharing is disabled');
    }

    const source = dto.source ?? 'last_known';
    let row = await this.locationRepo.findOne({ where: { userId } });
    if (!row) {
      row = this.locationRepo.create({
        userId,
        latitude: dto.latitude,
        longitude: dto.longitude,
        accuracyMeters: dto.accuracyMeters ?? null,
        source,
      });
    } else {
      row.latitude = dto.latitude;
      row.longitude = dto.longitude;
      row.accuracyMeters = dto.accuracyMeters ?? null;
      row.source = source;
    }
    const saved = await this.locationRepo.save(row);
    return { ok: true, updatedAt: saved.updatedAt.toISOString() };
  }

  async listFriendLocations(
    userId: string,
    viewerLat?: number,
    viewerLng?: number,
  ): Promise<FriendLocationDto[]> {
    const friends = await this.friendsService.listFriends(userId);
    const shareIds = friends
      .filter((f) => f.shareLocation === true)
      .map((f) => f.id);
    if (!shareIds.length) {
      return [];
    }

    const rows = await this.locationRepo
      .createQueryBuilder('loc')
      .innerJoinAndSelect('loc.user', 'u')
      .where('loc.userId IN (:...ids)', { ids: shareIds })
      .getMany();

    return rows.map((loc) => {
      const u = loc.user!;
      const item: FriendLocationDto = {
        userId: loc.userId,
        firstName: u.firstName ?? null,
        lastName: u.lastName ?? null,
        avatarUrl: u.avatarUrl ?? null,
        latitude: loc.latitude,
        longitude: loc.longitude,
        accuracyMeters: loc.accuracyMeters,
        source: loc.source,
        updatedAt: loc.updatedAt.toISOString(),
      };
      if (
        viewerLat !== undefined &&
        viewerLng !== undefined &&
        !Number.isNaN(viewerLat) &&
        !Number.isNaN(viewerLng)
      ) {
        item.distanceKm = haversineKm(
          viewerLat,
          viewerLng,
          loc.latitude,
          loc.longitude,
        );
      }
      return item;
    });
  }

  async discoverNearby(
    userId: string,
    lat: number,
    lng: number,
    radiusKm = 100,
  ): Promise<DiscoverUserDto[]> {
    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      throw new BadRequestException('lat and lng are required');
    }
    const cap = Math.min(Math.max(radiusKm, 1), 500);

    const friendIds = new Set(await this.friendsService.listFriendUserIds(userId));

    const locations = await this.locationRepo
      .createQueryBuilder('loc')
      .innerJoinAndSelect('loc.user', 'u')
      .where('loc.userId != :me', { me: userId })
      .andWhere('u.accountStatus = :active', {
        active: UserAccountStatus.ACTIVE,
      })
      .andWhere('u.share_location = true')
      .andWhere('u.show_in_friend_search = true')
      .getMany();

    const out: DiscoverUserDto[] = [];
    for (const loc of locations) {
      const u = loc.user!;
      if (friendIds.has(u.id)) {
        continue;
      }
      if (!isDiscoverableUser(u)) {
        continue;
      }
      const dist = haversineKm(lat, lng, loc.latitude, loc.longitude);
      if (dist > cap) {
        continue;
      }
      const blurred = blurCoordinates(loc.latitude, loc.longitude);
      out.push({
        userId: u.id,
        firstName: u.firstName ?? null,
        lastName: u.lastName ?? null,
        avatarUrl: u.avatarUrl ?? null,
        latitude: blurred.latitude,
        longitude: blurred.longitude,
        distanceKm: Math.round(dist * 10) / 10,
        updatedAt: loc.updatedAt.toISOString(),
      });
    }

    out.sort((a, b) => a.distanceKm - b.distanceKm);
    return out.slice(0, 50);
  }
}
