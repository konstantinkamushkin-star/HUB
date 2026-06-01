import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Request,
  Query,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { UsersService } from './users.service';
import { UserProfileSummaryService } from './user-profile-summary.service';
import { PushService } from '../push/push.service';
import { RegisterPushTokenDto } from './dto/register-push-token.dto';
import { DeleteMyAccountDto } from './dto/delete-my-account.dto';
import { LocationService } from '../location/location.service';
import { UpdatePrivacySettingsDto } from '../location/dto/update-privacy-settings.dto';
import { UpdateLocationSettingsDto } from '../location/dto/update-location-settings.dto';
import { ReportLocationDto } from '../location/dto/report-location.dto';

function extractForwardedIp(forwardedFor?: string): string | null {
  if (!forwardedFor) {
    return null;
  }
  const first = forwardedFor.split(',')[0]?.trim();
  return first && first.length > 0 ? first : null;
}

@ApiTags('users')
@Controller('users')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class UsersController {
  constructor(
    private readonly usersService: UsersService,
    private readonly pushService: PushService,
    private readonly locationService: LocationService,
    private readonly profileSummaryService: UserProfileSummaryService,
  ) {}

  @Post('me/push-token')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Register device token for push (APNs/FCM wiring comes later)',
  })
  async registerPushToken(
    @Request() req: { user: { sub: string } },
    @Body() dto: RegisterPushTokenDto,
  ) {
    await this.pushService.registerToken(
      req.user.sub,
      dto.token,
      dto.platform ?? 'ios',
    );
    return { ok: true };
  }

  @Patch('me/settings/privacy')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update privacy settings (location, search, logbook)' })
  updatePrivacy(
    @Request() req: { user: { sub: string } },
    @Body() dto: UpdatePrivacySettingsDto,
  ) {
    return this.locationService.updatePrivacySettings(req.user.sub, dto);
  }

  @Patch('me/location-settings')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update location sharing and discover opt-in' })
  updateLocationSettings(
    @Request() req: { user: { sub: string } },
    @Body() dto: UpdateLocationSettingsDto,
  ) {
    return this.locationService.updateLocationSettings(req.user.sub, dto);
  }

  @Post('me/location')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Report current or last-known location' })
  reportLocation(
    @Request() req: { user: { sub: string } },
    @Body() dto: ReportLocationDto,
  ) {
    return this.locationService.reportLocation(req.user.sub, dto);
  }

  @Get('discover/nearby')
  @ApiOperation({ summary: 'Discover divers nearby (opt-in, blurred coords)' })
  discoverNearby(
    @Request() req: { user: { sub: string } },
    @Query('lat') lat?: string,
    @Query('lng') lng?: string,
    @Query('radiusKm') radiusKm?: string,
  ) {
    return this.locationService.discoverNearby(
      req.user.sub,
      parseFloat(lat ?? 'NaN'),
      parseFloat(lng ?? 'NaN'),
      radiusKm ? parseFloat(radiusKm) : 100,
    );
  }

  @Get('search')
  @ApiOperation({ summary: 'Search users by email or name (min 2 chars)' })
  @ApiResponse({ status: 200, description: 'Matching users (max 50)' })
  async search(
    @Request() req: { user: { sub: string } },
    @Query('query') query?: string,
  ) {
    return this.usersService.search(req.user.sub, query ?? '');
  }

  @Get('me/export')
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @ApiOperation({ summary: 'Export my personal data (GDPR/DSAR)' })
  @ApiResponse({ status: 200, description: 'Personal data export payload' })
  exportMe(
    @Request() req: { user: { sub: string } },
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.usersService.exportMyData(req.user.sub, {
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: extractForwardedIp(forwardedFor),
    });
  }

  @Delete('me')
  @Throttle({ default: { limit: 2, ttl: 60_000 } })
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Delete (anonymize) my account data with re-auth checks' })
  @ApiResponse({ status: 200, description: 'Account deletion scheduled/completed' })
  @ApiResponse({ status: 400, description: 'Missing explicit delete confirmation' })
  @ApiResponse({ status: 401, description: 'Invalid current password for password account' })
  deleteMe(
    @Request() req: { user: { sub: string } },
    @Body() dto: DeleteMyAccountDto,
    @Headers('x-account-delete-confirm') deleteConfirmHeader?: string,
    @Headers('user-agent') userAgent?: string,
    @Headers('x-correlation-id') correlationId?: string,
    @Headers('x-forwarded-for') forwardedFor?: string,
  ) {
    return this.usersService.deleteMyAccount(req.user.sub, dto, {
      deleteConfirmHeader: deleteConfirmHeader ?? null,
      userAgent: userAgent ?? null,
      correlationId: correlationId ?? null,
      ip: extractForwardedIp(forwardedFor),
    });
  }

  @Get(':id/summary')
  @ApiOperation({ summary: 'Friend-aware profile summary (dives, countries, cert)' })
  async getSummary(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
  ) {
    return this.profileSummaryService.getSummary(id, req.user.sub);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get public user profile by id' })
  @ApiResponse({ status: 404, description: 'User not found' })
  async getById(@Param('id') id: string) {
    return this.usersService.findById(id);
  }
}
