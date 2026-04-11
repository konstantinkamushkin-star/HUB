import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { AuditLogService } from './audit-log.service';
import { MergeEntitiesDto } from './dto/merge-entities.dto';
import { DiveCenterStatus, DiveSiteStatus, UserAccountStatus } from '../common/statuses';

@Injectable()
export class AdminMergeService {
  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly sitesRepo: Repository<DiveSiteEntity>,
    @InjectRepository(FeedPost)
    private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  async mergeUsers(dto: MergeEntitiesDto, actor: any) {
    const source = await this.usersRepo.findOne({ where: { id: dto.sourceId } });
    const target = await this.usersRepo.findOne({ where: { id: dto.targetId } });
    if (!source || !target) throw new NotFoundException('User not found');

    await this.postsRepo
      .createQueryBuilder()
      .update(FeedPost)
      .set({ userId: target.id })
      .where('userId = :sid', { sid: source.id })
      .execute();

    await this.logsRepo
      .createQueryBuilder()
      .update(DiveLogEntity)
      .set({ userId: target.id })
      .where('userId = :sid', { sid: source.id })
      .execute();

    const before = { sourceStatus: source.accountStatus, sourceMergedInto: source.mergedIntoUserId ?? null };
    source.accountStatus = UserAccountStatus.MERGED;
    source.mergedIntoUserId = target.id;
    source.deletedAt = new Date();
    await this.usersRepo.save(source);

    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.merge.users',
      targetType: 'user',
      targetId: source.id,
      before,
      after: { sourceStatus: source.accountStatus, sourceMergedInto: source.mergedIntoUserId },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });

    return { sourceId: source.id, targetId: target.id, merged: true };
  }

  async mergeDiveCenters(dto: MergeEntitiesDto, actor: any) {
    const source = await this.centersRepo.findOne({ where: { id: dto.sourceId } });
    const target = await this.centersRepo.findOne({ where: { id: dto.targetId } });
    if (!source || !target) throw new NotFoundException('Dive center not found');

    const mergedSites = Array.from(new Set([...(target.affiliated_sites ?? []), ...(source.affiliated_sites ?? [])]));
    target.affiliated_sites = mergedSites;
    await this.centersRepo.save(target);

    const before = { sourceStatus: source.status, sourceDeletedAt: source.deleted_at ?? null };
    source.status = DiveCenterStatus.MERGED;
    source.deleted_at = new Date();
    source.is_active = false;
    await this.centersRepo.save(source);

    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.merge.dive_centers',
      targetType: 'dive_center',
      targetId: source.id,
      before,
      after: { sourceStatus: source.status, sourceDeletedAt: source.deleted_at, targetAffiliatedSites: target.affiliated_sites.length },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });

    return { sourceId: source.id, targetId: target.id, merged: true };
  }

  async mergeDiveSites(dto: MergeEntitiesDto, actor: any) {
    const source = await this.sitesRepo.findOne({ where: { id: dto.sourceId } });
    const target = await this.sitesRepo.findOne({ where: { id: dto.targetId } });
    if (!source || !target) throw new NotFoundException('Dive site not found');

    await this.logsRepo
      .createQueryBuilder()
      .update(DiveLogEntity)
      .set({ diveSiteId: target.id })
      .where('"diveSiteId" = :sid', { sid: source.id })
      .execute();

    const before = { sourceStatus: source.status, sourceDeletedAt: source.deleted_at ?? null };
    source.status = DiveSiteStatus.MERGED;
    source.deleted_at = new Date();
    source.is_active = false;
    await this.sitesRepo.save(source);

    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.merge.dive_sites',
      targetType: 'dive_site',
      targetId: source.id,
      before,
      after: { sourceStatus: source.status, sourceDeletedAt: source.deleted_at },
      reason: dto.reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });

    return { sourceId: source.id, targetId: target.id, merged: true };
  }
}
