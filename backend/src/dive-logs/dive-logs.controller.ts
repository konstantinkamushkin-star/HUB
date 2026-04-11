import { Body, Controller, Get, Post, Query, Request, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { DiveLogsService } from './dive-logs.service';
import { CreateDiveLogDto } from './dto/create-dive-log.dto';

@ApiTags('dive-logs')
@Controller('dive-logs')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class DiveLogsController {
  constructor(private readonly diveLogsService: DiveLogsService) {}

  @Post()
  @ApiOperation({ summary: 'Create a dive log' })
  async create(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateDiveLogDto,
  ) {
    return this.diveLogsService.create(req.user.sub, dto);
  }

  @Get()
  @ApiOperation({ summary: 'List own dive logs' })
  async list(
    @Request() req: { user: { sub: string } },
    @Query('userId') userId?: string,
  ) {
    return this.diveLogsService.listForUser(req.user.sub, userId);
  }

  @Get('public')
  @ApiOperation({ summary: 'List public dive logs by dive site' })
  async listPublicByDiveSite(
    @Query('diveSiteId') diveSiteId?: string,
    @Query('limit') limitStr?: string,
  ) {
    const limit = limitStr ? parseInt(limitStr, 10) : undefined;
    return this.diveLogsService.listPublicByDiveSite(
      diveSiteId,
      Number.isFinite(limit) ? limit : undefined,
    );
  }
}
