import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserCertificationEntity } from './entities/user-certification.entity';
import { CertificationsService } from './certifications.service';
import { CertificationsController } from './certifications.controller';
import { FriendsModule } from '../friends/friends.module';
import { User } from '../users/entities/user.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([UserCertificationEntity, User]),
    FriendsModule,
  ],
  controllers: [CertificationsController],
  providers: [CertificationsService],
  exports: [CertificationsService],
})
export class CertificationsModule {}
