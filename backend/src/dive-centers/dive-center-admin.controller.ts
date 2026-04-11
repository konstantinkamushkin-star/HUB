import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { DiveCentersService } from './dive-centers.service';

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
}
