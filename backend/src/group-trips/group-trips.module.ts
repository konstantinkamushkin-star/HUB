import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { GroupTripEntity } from './entities/group-trip.entity';
import { GroupTripMemberEntity } from './entities/group-trip-member.entity';
import { GroupTripsService } from './group-trips.service';
import { GroupTripsController } from './group-trips.controller';
import { ChatModule } from '../chat/chat.module';
import { FriendsModule } from '../friends/friends.module';
import { AuthModule } from '../auth/auth.module';
import { User } from '../users/entities/user.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([GroupTripEntity, GroupTripMemberEntity, User]),
    ChatModule,
    FriendsModule,
    AuthModule,
  ],
  controllers: [GroupTripsController],
  providers: [GroupTripsService],
  exports: [GroupTripsService],
})
export class GroupTripsModule {}
