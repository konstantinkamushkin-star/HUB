import { Controller, Get, Query, Request, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { LocationService } from './location.service';

@ApiTags('friends')
@Controller('friends')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class LocationFriendsController {
  constructor(private readonly locationService: LocationService) {}

  @Get('locations')
  @ApiOperation({ summary: 'Friend locations (shareLocation enabled)' })
  listFriendLocations(
    @Request() req: { user: { sub: string } },
    @Query('lat') lat?: string,
    @Query('lng') lng?: string,
  ) {
    const viewerLat = lat !== undefined ? parseFloat(lat) : undefined;
    const viewerLng = lng !== undefined ? parseFloat(lng) : undefined;
    return this.locationService.listFriendLocations(
      req.user.sub,
      viewerLat,
      viewerLng,
    );
  }
}
