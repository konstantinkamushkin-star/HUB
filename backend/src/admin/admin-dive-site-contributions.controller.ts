import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { AdminOrSuperAdminGuard } from './admin-or-super-admin.guard';
import { DiveSiteContributionsService } from '../dive-sites/dive-site-contributions.service';
import { RejectDiveSiteContributionDto } from '../dive-sites/dto/reject-dive-site-contribution.dto';

@ApiTags('admin-dive-site-contributions')
@Controller('admin/dive-site-contributions')
@UseGuards(JwtAuthGuard, AdminOrSuperAdminGuard)
@ApiBearerAuth()
export class AdminDiveSiteContributionsController {
  constructor(
    private readonly contributionsService: DiveSiteContributionsService,
  ) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'List dive site contributions (SUPER_ADMIN or ADMIN only)',
  })
  async list(
    @Query('status') status?: string,
    @Query('limit') limit?: string,
  ) {
    const parsed = limit ? Number(limit) : 50;
    const data = await this.contributionsService.listAdmin(
      status,
      Number.isFinite(parsed) ? parsed : 50,
    );
    return { success: true, data };
  }

  @Post(':id/approve')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Approve contribution and apply to dive_sites (SUPER_ADMIN or ADMIN only)',
  })
  async approve(
    @Param('id') id: string,
    @Req() req: { user: { sub: string } },
  ) {
    return this.contributionsService.approve(id, req.user.sub);
  }

  @Post(':id/reject')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Reject contribution (SUPER_ADMIN or ADMIN only)',
  })
  async reject(
    @Param('id') id: string,
    @Req() req: { user: { sub: string } },
    @Body() dto: RejectDiveSiteContributionDto,
  ) {
    return this.contributionsService.reject(id, req.user.sub, dto.reason);
  }
}
