import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { DiveSiteContributionsService } from './dive-site-contributions.service';
import { CreateDiveSiteContributionDto } from './dto/create-dive-site-contribution.dto';

@ApiTags('dive-site-contributions')
@Controller('v1/dive-sites/contributions')
export class DiveSiteContributionsController {
  constructor(
    private readonly contributionsService: DiveSiteContributionsService,
  ) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({
    summary:
      'Suggest a correction for an existing dive site or propose a new site (pending admin approval)',
  })
  async create(
    @Req() req: { user: { sub: string } },
    @Body() dto: CreateDiveSiteContributionDto,
  ) {
    const row = await this.contributionsService.create(req.user.sub, dto);
    return { success: true, data: row };
  }

  @Get('mine')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'List current user dive site contributions' })
  async mine(
    @Req() req: { user: { sub: string } },
    @Query('limit') limit?: string,
  ) {
    const parsed = limit ? Number(limit) : 30;
    const data = await this.contributionsService.listMine(
      req.user.sub,
      Number.isFinite(parsed) ? parsed : 30,
    );
    return { success: true, data };
  }

  @Get('stats/mine')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({
    summary: 'Aggregate counts for current user dive site contributions',
  })
  async statsMine(@Req() req: { user: { sub: string } }) {
    const data = await this.contributionsService.getMineStats(req.user.sub);
    return { success: true, data };
  }
}
