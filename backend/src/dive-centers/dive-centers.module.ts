import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveCentersController } from './dive-centers.controller';
import { DiveCenterAdminController } from './dive-center-admin.controller';
import { AdminMobileController } from './admin-mobile.controller';
import { DiveCentersService } from './dive-centers.service';
import { CenterGearService } from './center-gear.service';
import { CenterInventoryService } from './center-inventory.service';
import { DiveCenterEntity } from './entities/dive-center.entity';
import { User } from '../users/entities/user.entity';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([DiveCenterEntity, User]),
    AuthModule,
  ],
  controllers: [
    DiveCentersController,
    DiveCenterAdminController,
    AdminMobileController,
  ],
  providers: [DiveCentersService, CenterGearService, CenterInventoryService],
  exports: [DiveCentersService],
})
export class DiveCentersModule {}
