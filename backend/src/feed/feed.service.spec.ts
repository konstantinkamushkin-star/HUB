import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Brackets } from 'typeorm';
import { FeedPostStatus, UserAccountStatus } from '../common/statuses';
import { FriendsService } from '../friends/friends.service';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { User } from '../users/entities/user.entity';
import { FeedPost } from './entities/feed-post.entity';
import { FeedPostComment } from './entities/feed-post-comment.entity';
import { FeedPostLike } from './entities/feed-post-like.entity';
import { FeedService } from './feed.service';

function makePost(overrides: Partial<FeedPost> = {}): FeedPost {
  return {
    id: 'post-1',
    userId: 'author-1',
    type: 'text',
    content: 'Hello #dive',
    diveLogId: null,
    photos: [],
    videos: [],
    moderationStatus: FeedPostStatus.PUBLISHED,
    commentsEnabled: true,
    likesEnabled: true,
    deletedAt: undefined,
    createdAt: new Date('2026-01-01T00:00:00.000Z'),
    updatedAt: new Date('2026-01-01T00:00:00.000Z'),
    user: {
      id: 'author-1',
      accountStatus: UserAccountStatus.ACTIVE,
      deletedAt: undefined,
      publicProfile: false,
    } as User,
    ...overrides,
  } as FeedPost;
}

describe('FeedService unified feed', () => {
  let service: FeedService;
  let friendsService: { listFriendUserIds: jest.Mock };
  let postRepository: {
    createQueryBuilder: jest.Mock;
    findOne: jest.Mock;
    manager: { getRepository: jest.Mock };
  };
  let likeRepository: {
    createQueryBuilder: jest.Mock;
    find: jest.Mock;
    findOne: jest.Mock;
    count: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
    remove: jest.Mock;
  };
  let commentRepository: {
    createQueryBuilder: jest.Mock;
    find: jest.Mock;
    count: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
    findOne: jest.Mock;
  };
  let qb: {
    leftJoinAndSelect: jest.Mock;
    andWhere: jest.Mock;
    where: jest.Mock;
    orWhere: jest.Mock;
    orderBy: jest.Mock;
    addOrderBy: jest.Mock;
    take: jest.Mock;
    getMany: jest.Mock;
  };

  beforeEach(async () => {
    qb = {
      leftJoinAndSelect: jest.fn().mockReturnThis(),
      andWhere: jest.fn().mockReturnThis(),
      where: jest.fn().mockReturnThis(),
      orWhere: jest.fn().mockReturnThis(),
      orderBy: jest.fn().mockReturnThis(),
      addOrderBy: jest.fn().mockReturnThis(),
      take: jest.fn().mockReturnThis(),
      getMany: jest.fn().mockResolvedValue([]),
    };

    friendsService = {
      listFriendUserIds: jest.fn().mockResolvedValue([]),
    };

    postRepository = {
      createQueryBuilder: jest.fn().mockReturnValue(qb),
      findOne: jest.fn(),
      manager: {
        getRepository: jest.fn().mockReturnValue({
          findOne: jest.fn(),
        }),
      },
    };

    likeRepository = {
      createQueryBuilder: jest.fn().mockReturnValue({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([]),
      }),
      find: jest.fn().mockResolvedValue([]),
      findOne: jest.fn(),
      count: jest.fn().mockResolvedValue(0),
      create: jest.fn((x) => x),
      save: jest.fn(),
      remove: jest.fn(),
    };

    commentRepository = {
      createQueryBuilder: jest.fn().mockReturnValue({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([]),
      }),
      find: jest.fn().mockResolvedValue([]),
      count: jest.fn().mockResolvedValue(0),
      create: jest.fn((x) => x),
      save: jest.fn(),
      findOne: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        FeedService,
        { provide: getRepositoryToken(FeedPost), useValue: postRepository },
        { provide: getRepositoryToken(FeedPostLike), useValue: likeRepository },
        {
          provide: getRepositoryToken(FeedPostComment),
          useValue: commentRepository,
        },
        { provide: getRepositoryToken(DiveLogEntity), useValue: {} },
        { provide: getRepositoryToken(DiveSiteEntity), useValue: {} },
        { provide: FriendsService, useValue: friendsService },
      ],
    }).compile();

    service = module.get(FeedService);
  });

  it('listPosts applies unified visibility for users without friends', async () => {
    const strangerPost = makePost({
      id: 'post-stranger',
      userId: 'stranger-1',
      user: {
        id: 'stranger-1',
        accountStatus: UserAccountStatus.ACTIVE,
      } as User,
    });
    qb.getMany.mockResolvedValue([strangerPost]);

    const result = await service.listPosts('viewer-1');

    expect(friendsService.listFriendUserIds).toHaveBeenCalledWith('viewer-1');
    expect(postRepository.createQueryBuilder).toHaveBeenCalledWith('p');
    expect(qb.leftJoinAndSelect).toHaveBeenCalledWith('p.user', 'u');
    expect(qb.andWhere).toHaveBeenCalled();
    const visibilityCall = qb.andWhere.mock.calls.find(
      ([arg]) => arg instanceof Brackets,
    );
    expect(visibilityCall).toBeTruthy();
    expect(result.items).toHaveLength(1);
    expect(result.items[0].id).toBe('post-stranger');
  });

  it('listPosts filters by hashtag across unified feed', async () => {
    await service.listPosts('viewer-1', 20, null, 'dive');

    expect(qb.andWhere).toHaveBeenCalledWith(
      'LOWER(p.content) LIKE :hashtagPat',
      { hashtagPat: '%#dive%' },
    );
  });

  it('toggleLike allows liking a published post from a non-friend', async () => {
    const post = makePost({ userId: 'stranger-1' });
    postRepository.findOne
      .mockResolvedValueOnce(post)
      .mockResolvedValueOnce(post);
    likeRepository.findOne.mockResolvedValue(null);
    likeRepository.save.mockResolvedValue({});

    const result = await service.toggleLike('viewer-1', 'post-1');

    expect(result.id).toBe('post-1');
    expect(likeRepository.save).toHaveBeenCalled();
  });

  it('toggleLike rejects hidden posts from non-friends', async () => {
    const post = makePost({
      userId: 'stranger-1',
      moderationStatus: FeedPostStatus.HIDDEN,
    });
    postRepository.findOne.mockResolvedValue(post);

    await expect(service.toggleLike('viewer-1', 'post-1')).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });

  it('listPostsForProfile allows public profile viewers', async () => {
    const userRepo = postRepository.manager.getRepository(User);
    userRepo.findOne.mockResolvedValue({
      id: 'author-1',
      publicProfile: true,
    });
    qb.getMany.mockResolvedValue([]);

    await expect(
      service.listPostsForProfile('viewer-1', 'author-1'),
    ).resolves.toEqual({
      items: [],
      hasMore: false,
      nextCursor: null,
    });
  });

  it('listPostsForProfile rejects private profiles for non-friends', async () => {
    const userRepo = postRepository.manager.getRepository(User);
    userRepo.findOne.mockResolvedValue({
      id: 'author-1',
      publicProfile: false,
    });

    await expect(
      service.listPostsForProfile('viewer-1', 'author-1'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('assertPostVisible via listComments throws for missing post', async () => {
    postRepository.findOne.mockResolvedValue(null);

    await expect(service.listComments('viewer-1', 'missing')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
