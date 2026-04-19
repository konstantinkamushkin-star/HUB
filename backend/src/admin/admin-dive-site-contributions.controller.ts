import {
  BadRequestException,
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

  @Get('stats/submitters')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Per-user contribution counts (leaderboard; SUPER_ADMIN or ADMIN only)',
  })
  async submitterStats(@Query('limit') limit?: string) {
    const parsed = limit ? Number(limit) : 50;
    const data = await this.contributionsService.listSubmitterLeaderboard(
      Number.isFinite(parsed) ? parsed : 50,
    );
    return { success: true, data };
  }

  /**
   * Основной способ: query — реже ломается прокси/nginx, чем лишние сегменты пути.
   * GET /api/admin/dive-site-contributions/support-chat?contributionId=<uuid>
   */
  @Get('support-chat')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Ensure contribution support chat (query contributionId); returns conversationId + deep link',
  })
  async supportChatByQuery(
    @Query('contributionId') contributionId: string | undefined,
  ) {
    const id = contributionId?.trim();
    if (!id) {
      throw new BadRequestException('contributionId query parameter is required');
    }
    const data = await this.contributionsService.getSupportChatForAdmin(id);
    return { success: true, data };
  }

  /** Сегмент `support-chat` до `:id`, чтобы роут не пересекался с другими GET под `:id`. */
  @Get('support-chat/:id')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Ensure contribution support chat thread exists; returns conversationId + app deep link',
  })
  async supportChat(@Param('id') id: string) {
    const data = await this.contributionsService.getSupportChatForAdmin(id);
    return { success: true, data };
  }

  /**
   * Старый путь (`.../:id/support-chat`) — оставляем для кэша/старых сборок admin-web.
   */
  @Get(':id/support-chat')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Legacy: same as GET support-chat/:id',
  })
  async supportChatLegacy(@Param('id') id: string) {
    const data = await this.contributionsService.getSupportChatForAdmin(id);
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
