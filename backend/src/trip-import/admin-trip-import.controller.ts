import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { TripImportService } from './trip-import.service';
import { ImportTripFromUrlDto } from './dto/import-trip-from-url.dto';
import { ImportTripsFromListingDto } from './dto/import-trips-from-listing.dto';

@ApiTags('admin-trip-import')
@Controller('admin/trips/import')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminTripImportController {
  constructor(private readonly tripImport: TripImportService) {}

  @Post('url')
  @ApiOperation({
    summary:
      'Импорт одной поездки по прямой ссылке (HTML + OpenAI + зеркалирование фото в /api/media)',
  })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  importOne(@Body() dto: ImportTripFromUrlDto) {
    return this.tripImport.importTripFromUrl({
      url: dto.url,
      diveCenterId: dto.diveCenterId,
      maxImageCandidates: dto.maxImageCandidates,
      maxPhotosToMirror: dto.maxPhotosToMirror,
    });
  }

  @Post('listing')
  @ApiOperation({
    summary:
      'Импорт нескольких поездок: страница каталога → LLM выбирает ссылки на карточки туров → импорт каждой',
  })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  importListing(@Body() dto: ImportTripsFromListingDto) {
    return this.tripImport.importTripsFromListing({
      listingUrl: dto.listingUrl,
      diveCenterId: dto.diveCenterId,
      maxTrips: dto.maxTrips,
      maxListingLinks: dto.maxListingLinks,
    });
  }
}
