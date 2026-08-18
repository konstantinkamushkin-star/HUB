import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { AnalyticsEventEntity } from '../admin/entities/analytics-event.entity';
import { ProcessedMediaController } from './processed-media.controller';
import { ProcessedMediaService } from './processed-media.service';
import { ProcessedMediaEntity } from './entities/processed-media.entity';

@Module({
  imports: [
    AuthModule,
    TypeOrmModule.forFeature([ProcessedMediaEntity, AnalyticsEventEntity]),
  ],
  controllers: [ProcessedMediaController],
  providers: [ProcessedMediaService],
  exports: [ProcessedMediaService, TypeOrmModule],
})
export class ProcessedMediaModule {}
