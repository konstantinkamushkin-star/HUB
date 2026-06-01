import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from './entities/user.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { FriendsService } from '../friends/friends.service';
import {
  diverProfileRecord,
  privacyAllows,
} from '../common/diver-profile.util';
import { UserAccountStatus } from '../common/statuses';

export type UserProfileSummaryDto = {
  userId: string;
  isFriend: boolean;
  username: string | null;
  city: string | null;
  totalDives: number | null;
  certificationLevel: string | null;
  certifyingAgencies: string[] | null;
  countriesDived: string[] | null;
  uniqueDiveSitesCount: number | null;
  deepestDiveMeters: number | null;
};

@Injectable()
export class UserProfileSummaryService {
  constructor(
    @InjectRepository(User)
    private readonly userRepo: Repository<User>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    private readonly friendsService: FriendsService,
  ) {}

  async getSummary(
    targetUserId: string,
    viewerId: string,
  ): Promise<UserProfileSummaryDto> {
    const user = await this.userRepo.findOne({
      where: { id: targetUserId, accountStatus: UserAccountStatus.ACTIVE },
    });
    if (!user) {
      throw new NotFoundException('User not found');
    }

    const friendIds = await this.friendsService.listFriendUserIds(viewerId);
    const isFriend = friendIds.includes(targetUserId);
    const isSelf = viewerId === targetUserId;

    const dp = diverProfileRecord(user);
    const base: UserProfileSummaryDto = {
      userId: user.id,
      isFriend,
      username: null,
      city: null,
      totalDives: null,
      certificationLevel: null,
      certifyingAgencies: null,
      countriesDived: null,
      uniqueDiveSitesCount: null,
      deepestDiveMeters: null,
    };

    if (!isSelf && !isFriend && !user.publicProfile) {
      return base;
    }

    if (privacyAllows(user, 'showProfilePhoto', true) || isSelf) {
      base.username =
        typeof dp?.username === 'string' ? dp.username : user.username ?? null;
      base.city = typeof dp?.city === 'string' ? dp.city : null;
    }

    if (isSelf || privacyAllows(user, 'showNumberOfDives', true)) {
      base.totalDives = user.totalDives ?? 0;
    }

    if (isSelf || privacyAllows(user, 'showCertificationLevel', true)) {
      const level =
        typeof dp?.certificationLevel === 'string'
          ? dp.certificationLevel
          : null;
      base.certificationLevel = level;
      const agencies = dp?.certifyingAgencies;
      if (Array.isArray(agencies)) {
        base.certifyingAgencies = agencies.filter(
          (a): a is string => typeof a === 'string',
        );
      } else if (typeof dp?.certifyingAgency === 'string') {
        base.certifyingAgencies = [dp.certifyingAgency];
      }
    }

    const canSeeLogbook =
      isSelf || (isFriend && privacyAllows(user, 'showLogbook', false));

    if (canSeeLogbook) {
      const countries = await this.logsRepo
        .createQueryBuilder('dl')
        .leftJoin(DiveSiteEntity, 'ds', 'ds.id = dl."diveSiteId"')
        .where('dl."userId" = :uid', { uid: targetUserId })
        .andWhere('dl."deletedAt" IS NULL')
        .andWhere('ds.country IS NOT NULL')
        .select('DISTINCT ds.country', 'country')
        .getRawMany<{ country: string }>();

      base.countriesDived = countries
        .map((r) => r.country)
        .filter(Boolean)
        .sort();

      const stats = await this.logsRepo
        .createQueryBuilder('dl')
        .where('dl."userId" = :uid', { uid: targetUserId })
        .andWhere('dl."deletedAt" IS NULL')
        .select('COUNT(DISTINCT dl."diveSiteId")', 'sites')
        .addSelect('MAX(dl."maxDepth")', 'deepest')
        .getRawOne<{ sites: string; deepest: string | null }>();

      base.uniqueDiveSitesCount = parseInt(stats?.sites ?? '0', 10) || 0;
      const deepest = stats?.deepest ? parseFloat(stats.deepest) : null;
      base.deepestDiveMeters =
        deepest !== null && !Number.isNaN(deepest) ? deepest : null;
    }

    return base;
  }
}
