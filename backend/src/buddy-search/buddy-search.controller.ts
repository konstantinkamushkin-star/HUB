import {
  Controller,
  Get,
  Put,
  Delete,
  Body,
  Query,
  UseGuards,
  Request,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { BuddySearchService } from './buddy-search.service';
import { UpsertBuddySearchDto } from './dto/upsert-buddy-search.dto';
import { ListBuddySearchQueryDto } from './dto/list-buddy-search.dto';

@ApiTags('buddy-search')
@Controller('buddy-search')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class BuddySearchController {
  constructor(private readonly buddySearchService: BuddySearchService) {}

  @Get('me')
  @ApiOperation({ summary: 'My open Find buddy intent (place + dates)' })
  async me(@Request() req: { user: { sub: string } }) {
    const search = await this.buddySearchService.getMine(req.user.sub);
    return { search };
  }

  @Get('listings')
  @ApiOperation({
    summary:
      'Open buddy questionnaires from other divers (optional place/date/cert/language filters)',
  })
  async listings(
    @Request() req: { user: { sub: string } },
    @Query() query: ListBuddySearchQueryDto,
  ) {
    return this.buddySearchService.listListings(req.user.sub, query);
  }

  @Get('matches')
  @ApiOperation({
    summary: 'Divers whose place+dates overlap my open Find buddy search',
  })
  async matches(@Request() req: { user: { sub: string } }) {
    return this.buddySearchService.listMatches(req.user.sub);
  }

  @Put()
  @ApiOperation({
    summary:
      'Save Find buddy questionnaire and return place+time matches (not tied to trips)',
  })
  async upsert(
    @Request() req: { user: { sub: string } },
    @Body() dto: UpsertBuddySearchDto,
  ) {
    return this.buddySearchService.upsert(req.user.sub, dto);
  }

  @Delete()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Close my Find buddy search' })
  async close(@Request() req: { user: { sub: string } }) {
    await this.buddySearchService.close(req.user.sub);
    return {};
  }
}
