import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { MediaModule } from '../media/media.module';
import { TripImportService } from './trip-import.service';
import { AdminTripImportController } from './admin-trip-import.controller';
import { TripsWriteService } from '../trips/trips-write.service';

@Module({
  imports: [ConfigModule, MediaModule],
  controllers: [AdminTripImportController],
  providers: [TripImportService, TripsWriteService],
  exports: [TripImportService],
})
export class TripImportModule {}
