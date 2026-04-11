import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Query,
  Req,
  Request,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { TripImportService } from '../trip-import/trip-import.service';
import { ImportTripUrlDto } from './dto/import-trip-url.dto';
import { CreateTripDto } from './dto/create-trip.dto';
import { UpdateTripDto } from './dto/update-trip.dto';
import { TripsService } from './trips.service';
import { TripsWriteService } from './trips-write.service';

@ApiTags('trips')
@Controller('trips')
export class TripsController {
  constructor(
    private readonly tripsService: TripsService,
    private readonly tripImportService: TripImportService,
    private readonly tripsWrite: TripsWriteService,
  ) {}

  @Post()
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({
    summary: 'Create trip for a dive center you manage (mobile / partner)',
  })
  async createTrip(
    @Request() req: { user: { sub: string; role?: string } },
    @Body() dto: CreateTripDto,
  ) {
    if (dto.endDate < dto.startDate) {
      throw new BadRequestException('endDate must be on or after startDate');
    }
    await this.tripsWrite.assertUserCanImportTripsForDiveCenter(
      dto.diveCenterId,
      req.user.sub,
      req.user.role,
    );
    const isDaily = dto.tripType === 'daily';
    return this.tripsWrite.createTrip({
      organizerId: dto.diveCenterId,
      organizerType: 'dive_center',
      tripType: dto.tripType,
      hotelId: isDaily ? dto.hotelId ?? null : null,
      yachtId: !isDaily ? dto.yachtId ?? null : null,
      hotelLabel: isDaily ? dto.hotelLabel?.trim() || null : null,
      yachtLabel: !isDaily ? dto.yachtLabel?.trim() || null : null,
      country: dto.country.trim(),
      region: dto.region?.trim() || null,
      startDate: dto.startDate,
      endDate: dto.endDate,
      minimumCertificationLevel: dto.minimumCertificationLevel?.trim() || null,
      minimumDives: dto.minimumDives ?? null,
      description: dto.description.trim(),
      photoUrls: dto.photoUrls?.length ? dto.photoUrls : [],
      totalSpots: dto.totalSpots,
      nitroxAvailable: dto.nitroxAvailable ?? false,
      equipmentRentalAvailable: dto.equipmentRentalAvailable ?? false,
      groupLeaderId: dto.groupLeaderId ?? null,
      programDays: dto.programDays ?? [],
      additionalExpenses: dto.additionalExpenses ?? [],
      priceDetails:
        dto.priceDetails && typeof dto.priceDetails === 'object'
          ? dto.priceDetails
          : { currency: 'USD' },
      availableCourseIds: dto.availableCourseIds?.length
        ? dto.availableCourseIds
        : [],
    });
  }

  @Get()
  @HttpCode(HttpStatus.OK)
  async getTrips(
    @Query('tripType') tripType?: string,
    @Query('country') country?: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
    @Query('minCertificationLevel') minCertificationLevel?: string,
    @Query('nitroxAvailable') nitroxAvailable?: string,
    @Query('equipmentRentalAvailable') equipmentRentalAvailable?: string,
    @Query('availableSpots') availableSpots?: string,
    @Query('organizerId') organizerId?: string,
  ) {
    try {
      const trips = await this.tripsService.getTrips({
        tripType,
        country,
        startDate,
        endDate,
        minCertificationLevel,
        nitroxAvailable: nitroxAvailable === 'true',
        equipmentRentalAvailable: equipmentRentalAvailable === 'true',
        availableSpots: availableSpots === 'true',
        organizerId: organizerId?.trim() || undefined,
      });
      return trips;
    } catch (error) {
      console.error('Error in getTrips endpoint:', error);
      throw error;
    }
  }

  @Patch(':id')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update trip (organizer / dive center you manage)' })
  async updateTrip(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('id') id: string,
    @Body() dto: UpdateTripDto,
  ) {
    if (dto.endDate < dto.startDate) {
      throw new BadRequestException('endDate must be on or after startDate');
    }
    return this.tripsWrite.updateTripById(id, dto, req.user.sub, req.user.role);
  }

  @Delete(':id')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Delete trip with no bookings (organizer / managed center)' })
  async deleteTrip(
    @Request() req: { user: { sub: string; role?: string } },
    @Param('id') id: string,
  ) {
    await this.tripsWrite.deleteTripById(id, req.user.sub, req.user.role);
  }

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  async getTrip(@Param('id') id: string) {
    try {
      const trip = await this.tripsService.getTrip(id);
      return trip;
    } catch (error) {
      console.error('Error in getTrip endpoint:', error);
      throw error;
    }
  }

  @Post(':id/join')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Join trip as diver (one spot, MVP; no payment)' })
  @ApiResponse({ status: 200, description: 'Joined' })
  @ApiResponse({ status: 409, description: 'Already joined or could not claim spot' })
  async joinTrip(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
  ) {
    return this.tripsService.joinTrip(req.user.sub, id);
  }

  @Post('import/url')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async importTripFromUrl(@Req() req: any, @Body() dto: ImportTripUrlDto) {
    return this.tripImportService.importTripFromUrlForOwner({
      url: dto.url,
      diveCenterId: dto.diveCenterId,
      userId: req.user?.sub,
      userRole: req.user?.role,
    });
  }
}
