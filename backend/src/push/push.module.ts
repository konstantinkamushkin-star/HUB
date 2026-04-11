import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserPushDevice } from './entities/user-push-device.entity';
import { PushService } from './push.service';

@Module({
  imports: [TypeOrmModule.forFeature([UserPushDevice])],
  providers: [PushService],
  exports: [PushService],
})
export class PushModule {}
