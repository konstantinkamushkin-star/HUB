import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveCentersController } from './dive-centers.controller';
import { DiveCenterAdminController } from './dive-center-admin.controller';
import { DiveCentersService } from './dive-centers.service';
import { DiveCenterEntity } from './entities/dive-center.entity';
import { User } from '../users/entities/user.entity';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([DiveCenterEntity, User]),
    AuthModule,
  ],
  controllers: [DiveCentersController, DiveCenterAdminController],
  providers: [DiveCentersService],
  exports: [DiveCentersService],
})
export class DiveCentersModule {}
