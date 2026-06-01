import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../users/entities/user.entity';
import { UserLocationEntity } from './entities/user-location.entity';
import { LocationService } from './location.service';
import { FriendsModule } from '../friends/friends.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([UserLocationEntity, User]),
    FriendsModule,
    AuthModule,
  ],
  providers: [LocationService],
  exports: [LocationService],
})
export class LocationModule {}
