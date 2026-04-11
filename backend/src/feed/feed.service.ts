import {
  Injectable,
  BadRequestException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { FriendsService } from '../friends/friends.service';
import { FeedPost } from './entities/feed-post.entity';
import { FeedPostLike } from './entities/feed-post-like.entity';
import { FeedPostComment } from './entities/feed-post-comment.entity';
import { CreateFeedPostDto } from './dto/create-feed-post.dto';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';

function toPublicUser(user: User): Omit<User, 'password'> {
  const { password: _, ...rest } = user;
  return rest;
}

const DEFAULT_FEED_LIMIT = 20;
const MAX_FEED_LIMIT = 50;

function encodeFeedCursor(post: FeedPost): string {
  return Buffer.from(
    `${post.createdAt.toISOString()}|${post.id}`,
    'utf8',
  ).toString('base64url');
}

function decodeFeedCursor(cursor: string): { t: Date; id: string } | null {
  try {
    const raw = Buffer.from(cursor, 'base64url').toString('utf8');
    const pipe = raw.lastIndexOf('|');
    if (pipe <= 0) {
      return null;
    }
    const iso = raw.slice(0, pipe);
    const id = raw.slice(pipe + 1);
    const t = new Date(iso);
    if (Number.isNaN(+t) || !id) {
      return null;
    }
    return { t, id };
  } catch {
    return null;
  }
}

@Injectable()
export class FeedService {
  constructor(
    @InjectRepository(FeedPost)
    private readonly postRepository: Repository<FeedPost>,
    @InjectRepository(FeedPostLike)
    private readonly likeRepository: Repository<FeedPostLike>,
    @InjectRepository(FeedPostComment)
    private readonly commentRepository: Repository<FeedPostComment>,
    @InjectRepository(DiveLogEntity)
    private readonly diveLogRepository: Repository<DiveLogEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly diveSiteRepository: Repository<DiveSiteEntity>,
    private readonly friendsService: FriendsService,
  ) {}

  private async visibleAuthorIds(viewerId: string): Promise<string[]> {
    const friendIds = await this.friendsService.listFriendUserIds(viewerId);
    return [...new Set([viewerId, ...friendIds])];
  }

  private async assertPostVisible(postId: string, viewerId: string) {
    const post = await this.postRepository.findOne({ where: { id: postId } });
    if (!post) {
      throw new NotFoundException('Post not found');
    }
    const allowed = await this.visibleAuthorIds(viewerId);
    if (!allowed.includes(post.userId)) {
      throw new ForbiddenException('Post not visible');
    }
    return post;
  }

  private async enrichPosts(posts: FeedPost[], viewerId: string) {
    if (posts.length === 0) {
      return [];
    }
    const ids = posts.map((p) => p.id);
    const likeRows = await this.likeRepository
      .createQueryBuilder('l')
      .select('l.postId', 'postId')
      .addSelect('COUNT(*)', 'cnt')
      .where('l.postId IN (:...ids)', { ids })
      .groupBy('l.postId')
      .getRawMany<{ postId: string; cnt: string }>();
    const commentRows = await this.commentRepository
      .createQueryBuilder('c')
      .select('c.postId', 'postId')
      .addSelect('COUNT(*)', 'cnt')
      .where('c.postId IN (:...ids)', { ids })
      .groupBy('c.postId')
      .getRawMany<{ postId: string; cnt: string }>();
    const myLikes = await this.likeRepository.find({
      where: { userId: viewerId, postId: In(ids) },
    });
    const likedSet = new Set(myLikes.map((l) => l.postId));
    const likeMap = new Map(
      likeRows.map((r) => [r.postId, parseInt(r.cnt, 10) || 0]),
    );
    const commentMap = new Map(
      commentRows.map((r) => [r.postId, parseInt(r.cnt, 10) || 0]),
    );
    return Promise.all(
      posts.map((p) =>
        this.serializePost(
          p,
          viewerId,
          likeMap.get(p.id) ?? 0,
          commentMap.get(p.id) ?? 0,
          likedSet.has(p.id),
        ),
      ),
    );
  }

  private async serializePost(
    post: FeedPost,
    viewerId: string,
    likeCount: number,
    commentCount: number,
    isLiked: boolean,
  ) {
    const user =
      post.user ??
      (await this.postRepository.manager.getRepository(User).findOne({
        where: { id: post.userId },
      }));
    if (!user) {
      throw new NotFoundException('Author not found');
    }
    let diveLog: Record<string, unknown> | null = null;
    if (post.diveLogId) {
      const linkedDiveLog = await this.diveLogRepository.findOne({
        where: { id: post.diveLogId },
      });
      if (linkedDiveLog) {
        let diveSiteName: string | null = null;
        if (linkedDiveLog.diveSiteId) {
          const site = await this.diveSiteRepository.findOne({
            where: { id: linkedDiveLog.diveSiteId },
            select: ['name'],
          });
          diveSiteName = site?.name ?? null;
        }
        diveLog = {
          id: linkedDiveLog.id,
          userId: linkedDiveLog.userId,
          diveSiteId: linkedDiveLog.diveSiteId,
          diveSiteName,
          date: linkedDiveLog.date,
          startTime: linkedDiveLog.startTime,
          endTime: linkedDiveLog.endTime,
          duration: linkedDiveLog.duration,
          maxDepth: linkedDiveLog.maxDepth,
          averageDepth: linkedDiveLog.averageDepth,
          waterTemperature: linkedDiveLog.waterTemperature,
          visibility: linkedDiveLog.visibility,
          current: linkedDiveLog.current,
          diveType: linkedDiveLog.diveType,
          notes: linkedDiveLog.notes,
          photoUrls: Array.isArray(linkedDiveLog.photoUrls)
            ? linkedDiveLog.photoUrls
            : [],
          videoUrls: Array.isArray(linkedDiveLog.videoUrls)
            ? linkedDiveLog.videoUrls
            : [],
          fishSpecies: Array.isArray(linkedDiveLog.fishSpecies)
            ? linkedDiveLog.fishSpecies
            : [],
          isPublished: linkedDiveLog.isPublished,
          createdAt: linkedDiveLog.createdAt,
          updatedAt: linkedDiveLog.updatedAt,
        };
      }
    }
    return {
      id: post.id,
      userId: post.userId,
      user: toPublicUser(user),
      type: post.type,
      content: post.content,
      diveLogId: post.diveLogId,
      diveLog,
      photos: Array.isArray(post.photos) ? post.photos : [],
      likes: likeCount,
      comments: commentCount,
      isLiked,
      createdAt: post.createdAt,
      updatedAt: post.updatedAt,
    };
  }

  async listPostsForProfile(
    viewerId: string,
    profileUserId: string,
    limit = DEFAULT_FEED_LIMIT,
    cursor?: string | null,
  ) {
    if (viewerId !== profileUserId) {
      const friends = await this.friendsService.listFriendUserIds(viewerId);
      if (!friends.includes(profileUserId)) {
        throw new ForbiddenException('Not allowed to view this profile feed');
      }
    }
    const lim = Math.min(Math.max(limit, 1), MAX_FEED_LIMIT);
    const qb = this.postRepository
      .createQueryBuilder('p')
      .leftJoinAndSelect('p.user', 'u')
      .where('p.userId = :uid', { uid: profileUserId })
      .orderBy('p.createdAt', 'DESC')
      .addOrderBy('p.id', 'DESC')
      .take(lim + 1);

    if (cursor) {
      const c = decodeFeedCursor(cursor);
      if (c) {
        qb.andWhere(
          '(p.createdAt < :ct OR (p.createdAt = :ct AND p.id < :pid))',
          { ct: c.t, pid: c.id },
        );
      }
    }

    const rows = await qb.getMany();
    const hasMore = rows.length > lim;
    const slice = hasMore ? rows.slice(0, lim) : rows;
    const items = await this.enrichPosts(slice, viewerId);
    const nextCursor =
      hasMore && slice.length ? encodeFeedCursor(slice[slice.length - 1]) : null;
    return { items, hasMore, nextCursor };
  }

  async listPosts(
    viewerId: string,
    limit = DEFAULT_FEED_LIMIT,
    cursor?: string | null,
  ) {
    const authorIds = await this.visibleAuthorIds(viewerId);
    const lim = Math.min(Math.max(limit, 1), MAX_FEED_LIMIT);
    const qb = this.postRepository
      .createQueryBuilder('p')
      .leftJoinAndSelect('p.user', 'u')
      .where('p.userId IN (:...authorIds)', { authorIds })
      .orderBy('p.createdAt', 'DESC')
      .addOrderBy('p.id', 'DESC')
      .take(lim + 1);

    if (cursor) {
      const c = decodeFeedCursor(cursor);
      if (c) {
        qb.andWhere(
          '(p.createdAt < :ct OR (p.createdAt = :ct AND p.id < :pid))',
          { ct: c.t, pid: c.id },
        );
      }
    }

    const rows = await qb.getMany();
    const hasMore = rows.length > lim;
    const slice = hasMore ? rows.slice(0, lim) : rows;
    const items = await this.enrichPosts(slice, viewerId);
    const nextCursor =
      hasMore && slice.length ? encodeFeedCursor(slice[slice.length - 1]) : null;
    return { items, hasMore, nextCursor };
  }

  async createPost(authorId: string, dto: CreateFeedPostDto) {
    if (dto.type === 'text' && !(dto.content?.trim()?.length)) {
      throw new BadRequestException('Text posts require content');
    }
    if (dto.type === 'dive' && !dto.diveLogId) {
      throw new BadRequestException('Dive posts require diveLogId');
    }
    const photos = dto.photos ?? [];
    const row = this.postRepository.create({
      userId: authorId,
      type: dto.type,
      content: dto.content?.trim() || null,
      diveLogId: dto.diveLogId ?? null,
      photos,
    });
    const saved = await this.postRepository.save(row);
    const post = await this.postRepository.findOne({
      where: { id: saved.id },
      relations: ['user'],
    });
    if (!post) {
      throw new NotFoundException('Post not found after create');
    }
    return this.serializePost(post, authorId, 0, 0, false);
  }

  async toggleLike(viewerId: string, postId: string) {
    await this.assertPostVisible(postId, viewerId);
    const existing = await this.likeRepository.findOne({
      where: { postId, userId: viewerId },
    });
    if (existing) {
      await this.likeRepository.remove(existing);
    } else {
      await this.likeRepository.save(
        this.likeRepository.create({ postId, userId: viewerId }),
      );
    }
    const post = await this.postRepository.findOne({
      where: { id: postId },
      relations: ['user'],
    });
    if (!post) {
      throw new NotFoundException('Post not found');
    }
    const likeCount = await this.likeRepository.count({ where: { postId } });
    const commentCount = await this.commentRepository.count({ where: { postId } });
    const isLiked = !!(await this.likeRepository.findOne({
      where: { postId, userId: viewerId },
    }));
    return this.serializePost(post, viewerId, likeCount, commentCount, isLiked);
  }

  async listComments(viewerId: string, postId: string) {
    await this.assertPostVisible(postId, viewerId);
    const rows = await this.commentRepository.find({
      where: { postId },
      relations: ['user'],
      order: { createdAt: 'ASC' },
    });
    return rows.map((c) => ({
      id: c.id,
      userId: c.userId,
      user: c.user ? toPublicUser(c.user) : undefined,
      postId: c.postId,
      content: c.content,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt,
    }));
  }

  async addComment(viewerId: string, postId: string, content: string) {
    await this.assertPostVisible(postId, viewerId);
    const text = content.trim();
    if (!text) {
      throw new BadRequestException('Comment cannot be empty');
    }
    const row = this.commentRepository.create({
      postId,
      userId: viewerId,
      content: text,
    });
    const saved = await this.commentRepository.save(row);
    const full = await this.commentRepository.findOne({
      where: { id: saved.id },
      relations: ['user'],
    });
    if (!full || !full.user) {
      throw new NotFoundException('Comment not found after create');
    }
    return {
      id: full.id,
      userId: full.userId,
      user: toPublicUser(full.user),
      postId: full.postId,
      content: full.content,
      createdAt: full.createdAt,
      updatedAt: full.updatedAt,
    };
  }
}
