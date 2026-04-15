import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { BookingsService } from './bookings.service';

@ApiTags('instructor-bookings')
@Controller('instructor/bookings')
export class BookingsInstructorController {
  constructor(private readonly bookingsService: BookingsService) {}

  @Get()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Get bookings for instructor schedule' })
  async getInstructorBookings(
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.bookingsService.getInstructorBookings(
      req.user.sub,
      req.user.role,
    );
  }

  @Post(':bookingId/complete')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Mark booking as completed by instructor' })
  async completeBooking(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('bookingId') bookingId: string,
  ) {
    return this.bookingsService.markCompletedByInstructor(
      bookingId,
      req.user.sub,
      req.user.role,
    );
  }
}
