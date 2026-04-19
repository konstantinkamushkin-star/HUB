import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { DiveCentersService } from './dive-centers.service';
import { PatchAffiliatedSitesDto } from './dto/patch-affiliated-sites.dto';

/**
 * Маршруты для владельца/админа дайв-центра в мобильном приложении (JWT, без web PermissionsGuard).
 */
@ApiTags('admin-centers')
@ApiBearerAuth()
@Controller('admin/centers')
@UseGuards(JwtAuthGuard)
export class DiveCenterAdminController {
  constructor(private readonly diveCentersService: DiveCentersService) {}

  @Get('managed')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Dive centers the user can manage (create trips, import, etc.)',
  })
  listManaged(
    @Request() req: { user: { sub: string; role?: string } },
  ): Promise<{ id: string; name: string }[]> {
    return this.diveCentersService.listCentersManagedForTripCreation(
      req.user.sub,
      req.user.role,
    );
  }

  @Get(':centerId/instructors')
  @HttpCode(HttpStatus.OK)
  listInstructors(
    @Param('centerId') centerId: string,
    @Request() req: { user: { sub: string } },
  ) {
    return this.diveCentersService.listInstructorUsersForCenterAdmin(
      centerId,
      req.user.sub,
    );
  }

  @Get(':centerId/affiliated-sites')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'UUIDs of dive sites affiliated with this center (mobile admin)',
  })
  getAffiliatedSites(
    @Param('centerId') centerId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ): Promise<{ siteIds: string[] }> {
    return this.diveCentersService.getAffiliatedSitesForCenterAdmin(
      centerId,
      req.user.sub,
      req.user.role,
    );
  }

  @Patch(':centerId/affiliated-sites')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Replace affiliated dive sites for this center (mobile admin)',
  })
  patchAffiliatedSites(
    @Param('centerId') centerId: string,
    @Body() dto: PatchAffiliatedSitesDto,
    @Request() req: { user: { sub: string; role?: string } },
  ): Promise<{ siteIds: string[] }> {
    return this.diveCentersService.setAffiliatedSitesForCenterAdmin(
      centerId,
      req.user.sub,
      req.user.role,
      dto.siteIds ?? [],
    );
  }
}
