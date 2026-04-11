import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';

function clampLimit(n?: number, def = 50, max = 200) {
  return Math.min(Math.max(n ?? def, 1), max);
}

function clampOffset(n?: number) {
  return Math.max(n ?? 0, 0);
}

@Injectable()
export class AdminRegistryService {
  constructor(
    @InjectRepository(DiveLogEntity)
    private readonly diveLogs: Repository<DiveLogEntity>,
    @InjectRepository(FeedPost)
    private readonly posts: Repository<FeedPost>,
    @InjectRepository(FeedPostComment)
    private readonly comments: Repository<FeedPostComment>,
    @InjectRepository(DiveCenterEntity)
    private readonly centers: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private readonly shops: Repository<ShopEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly sites: Repository<DiveSiteEntity>,
  ) {}

  async listDiveLogs(params: {
    limit?: number;
    offset?: number;
    moderationStatus?: string;
    userId?: string;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.diveLogs
      .createQueryBuilder('l')
      .orderBy('l.createdAt', 'DESC')
      .skip(offset)
      .take(limit);
    if (params.moderationStatus) {
      qb.andWhere('l.moderationStatus = :ms', { ms: params.moderationStatus });
    }
    if (params.userId) {
      qb.andWhere('l.userId = :uid', { uid: params.userId });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async listFeedPosts(params: {
    limit?: number;
    offset?: number;
    moderationStatus?: string;
    userId?: string;
    includeDeleted?: boolean;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.posts
      .createQueryBuilder('p')
      .orderBy('p.createdAt', 'DESC')
      .skip(offset)
      .take(limit);
    if (!params.includeDeleted) {
      qb.andWhere('p.deletedAt IS NULL');
    }
    if (params.moderationStatus) {
      qb.andWhere('p.moderationStatus = :ms', { ms: params.moderationStatus });
    }
    if (params.userId) {
      qb.andWhere('p.userId = :uid', { uid: params.userId });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async listComments(params: {
    limit?: number;
    offset?: number;
    moderationStatus?: string;
    postId?: string;
    userId?: string;
    includeDeleted?: boolean;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.comments
      .createQueryBuilder('c')
      .orderBy('c.createdAt', 'DESC')
      .skip(offset)
      .take(limit);
    if (!params.includeDeleted) {
      qb.andWhere('c.deletedAt IS NULL');
    }
    if (params.moderationStatus) {
      qb.andWhere('c.moderationStatus = :ms', { ms: params.moderationStatus });
    }
    if (params.postId) {
      qb.andWhere('c.postId = :pid', { pid: params.postId });
    }
    if (params.userId) {
      qb.andWhere('c.userId = :uid', { uid: params.userId });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async listDiveCenters(params: {
    limit?: number;
    offset?: number;
    status?: string;
    verificationStatus?: string;
    query?: string;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.centers
      .createQueryBuilder('c')
      .orderBy('c.created_at', 'DESC')
      .skip(offset)
      .take(limit);
    if (params.status) {
      qb.andWhere('c.status = :st', { st: params.status });
    }
    if (params.verificationStatus) {
      qb.andWhere('c.verification_status = :vs', { vs: params.verificationStatus });
    }
    if (params.query?.trim()) {
      const q = `%${params.query.trim()}%`;
      qb.andWhere('(LOWER(c.name) LIKE LOWER(:q) OR c.id::text = :exact)', {
        q,
        exact: params.query.trim(),
      });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async listShops(params: {
    limit?: number;
    offset?: number;
    verificationStatus?: string;
    query?: string;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.shops
      .createQueryBuilder('s')
      .orderBy('s.created_at', 'DESC')
      .skip(offset)
      .take(limit);
    if (params.verificationStatus) {
      qb.andWhere('s.verification_status = :vs', { vs: params.verificationStatus });
    }
    if (params.query?.trim()) {
      const q = `%${params.query.trim()}%`;
      qb.andWhere('(LOWER(s.name) LIKE LOWER(:q) OR s.id::text = :exact)', {
        q,
        exact: params.query.trim(),
      });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async listDiveSites(params: {
    limit?: number;
    offset?: number;
    status?: string;
    query?: string;
  }) {
    const limit = clampLimit(params.limit);
    const offset = clampOffset(params.offset);
    const qb = this.sites
      .createQueryBuilder('s')
      .orderBy('s.created_at', 'DESC')
      .skip(offset)
      .take(limit);
    if (params.status) {
      qb.andWhere('s.status = :st', { st: params.status });
    }
    if (params.query?.trim()) {
      const q = `%${params.query.trim()}%`;
      qb.andWhere('(LOWER(s.name) LIKE LOWER(:q) OR s.id::text = :exact)', {
        q,
        exact: params.query.trim(),
      });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }
}
