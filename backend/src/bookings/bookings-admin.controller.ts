import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { BookingsService } from './bookings.service';
import { UpdateBookingStatusDto } from './dto/update-booking-status.dto';

@ApiTags('admin-bookings')
@Controller('admin/bookings')
export class BookingsAdminController {
  constructor(private readonly bookingsService: BookingsService) {}

  @Get()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Get bookings for admin or managed centers' })
  async getAdminBookings(
    @Request() req: { user: { sub: string; role?: string } },
    @Query('centerId') centerId?: string,
  ) {
    return this.bookingsService.getAdminBookings(
      req.user.sub,
      req.user.role,
      centerId,
    );
  }

  @Patch(':bookingId/status')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update booking status' })
  async updateStatus(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('bookingId') bookingId: string,
    @Body() dto: UpdateBookingStatusDto,
  ) {
    return this.bookingsService.updateBookingStatus(
      bookingId,
      dto.status,
      req.user.sub,
      req.user.role,
      dto.finalPriceAmount,
      dto.finalPriceCurrency,
      dto.manualVerificationNote,
    );
  }
}
