import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveLogEntity } from './entities/dive-log.entity';
import { DiveLogsController } from './dive-logs.controller';
import { DiveLogsService } from './dive-logs.service';

@Module({
  imports: [TypeOrmModule.forFeature([DiveLogEntity])],
  controllers: [DiveLogsController],
  providers: [DiveLogsService],
})
export class DiveLogsModule {}
