import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Query,
  Request,
  UseGuards,
  Body,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { BookingsService } from './bookings.service';
import { BookingsStripeService } from './bookings-stripe.service';
import { CreateBookingDto } from './dto/create-booking.dto';
import { CreatePaymentIntentDto } from './dto/create-payment-intent.dto';

@ApiTags('bookings')
@Controller('bookings')
export class BookingsController {
  constructor(
    private readonly bookingsService: BookingsService,
    private readonly bookingsStripe: BookingsStripeService,
  ) {}

  @Post()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Create booking request' })
  async createBooking(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateBookingDto,
  ) {
    return this.bookingsService.createBooking(req.user.sub, dto);
  }

  @Post('payment-intent')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Create Stripe PaymentIntent for an online booking deposit (requires STRIPE_SECRET_KEY)',
  })
  async createPaymentIntent(
    @Body() dto: CreatePaymentIntentDto,
  ) {
    return this.bookingsStripe.createPaymentIntent(
      dto.amount,
      dto.currency,
      dto.diveCenterId,
    );
  }

  @Get()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Get user bookings (or admin query by userId)' })
  async getBookings(
    @Request() req: { user: { sub: string; role?: string } },
    @Query('userId') userId?: string,
  ) {
    return this.bookingsService.getBookingsForUser(
      req.user.sub,
      req.user.role,
      userId,
    );
  }
}
