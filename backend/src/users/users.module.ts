import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { AdminModule } from '../admin/admin.module';
import { PushModule } from '../push/push.module';
import { User } from './entities/user.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { UserPushDevice } from '../push/entities/user-push-device.entity';
import { UsersService } from './users.service';
import { UsersController } from './users.controller';
import { LocationModule } from '../location/location.module';
import { FriendsModule } from '../friends/friends.module';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { UserProfileSummaryService } from './user-profile-summary.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      User,
      FeedPost,
      FeedPostComment,
      DiveLogEntity,
      UserPushDevice,
      DiveSiteEntity,
    ]),
    AuthModule,
    AdminModule,
    PushModule,
    LocationModule,
    FriendsModule,
  ],
  controllers: [UsersController],
  providers: [UsersService, UserProfileSummaryService],
  exports: [UsersService, UserProfileSummaryService],
})
export class UsersModule {}
