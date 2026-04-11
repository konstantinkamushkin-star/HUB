import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { TripsController } from './trips.controller';
import { TripsService } from './trips.service';
import { TripsWriteService } from './trips-write.service';
import { TripImportModule } from '../trip-import/trip-import.module';

@Module({
  imports: [TypeOrmModule.forFeature([]), TripImportModule, AuthModule],
  controllers: [TripsController],
  providers: [TripsService, TripsWriteService],
  exports: [TripsService, TripsWriteService],
})
export class TripsModule {}
