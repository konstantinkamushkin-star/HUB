import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AnalyticsEventEntity } from '../admin/entities/analytics-event.entity';
import { AnalyticsIngestController } from './analytics-ingest.controller';

@Module({
  imports: [TypeOrmModule.forFeature([AnalyticsEventEntity])],
  controllers: [AnalyticsIngestController],
})
export class AnalyticsModule {}
