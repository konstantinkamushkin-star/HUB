import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { FriendsModule } from '../friends/friends.module';
import { FeedPost } from './entities/feed-post.entity';
import { FeedPostLike } from './entities/feed-post-like.entity';
import { FeedPostComment } from './entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { FeedService } from './feed.service';
import { FeedController } from './feed.controller';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      FeedPost,
      FeedPostLike,
      FeedPostComment,
      DiveLogEntity,
      DiveSiteEntity,
    ]),
    AuthModule,
    FriendsModule,
  ],
  controllers: [FeedController],
  providers: [FeedService],
  exports: [FeedService],
})
export class FeedModule {}
