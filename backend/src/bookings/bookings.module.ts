import { Module } from '@nestjs/common';
import { BookingsController } from './bookings.controller';
import { BookingsService } from './bookings.service';
import { BookingsAdminController } from './bookings-admin.controller';
import { BookingsInstructorController } from './bookings-instructor.controller';

@Module({
  controllers: [
    BookingsController,
    BookingsAdminController,
    BookingsInstructorController,
  ],
  providers: [BookingsService],
  exports: [BookingsService],
})
export class BookingsModule {}
