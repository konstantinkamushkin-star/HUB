import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Public } from '../auth/decorators/public.decorator';
import { AnalyticsEventEntity } from '../admin/entities/analytics-event.entity';
import { IngestAnalyticsEventDto } from './dto/ingest-analytics-event.dto';

@ApiTags('analytics')
@Controller('analytics')
export class AnalyticsIngestController {
  constructor(
    @InjectRepository(AnalyticsEventEntity)
    private readonly eventsRepo: Repository<AnalyticsEventEntity>,
  ) {}

  @Post('events')
  @Public()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Ingest a product analytics event' })
  async ingest(@Body() dto: IngestAnalyticsEventDto) {
    const row = this.eventsRepo.create({
      name: dto.name,
      properties: dto.properties ?? null,
      userId: dto.userId ?? null,
      sessionId: dto.sessionId ?? null,
      source: dto.source ?? null,
    });
    const saved = await this.eventsRepo.save(row);
    return { id: saved.id };
  }
}
