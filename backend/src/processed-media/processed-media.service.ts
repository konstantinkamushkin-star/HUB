import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AnalyticsEventEntity } from '../admin/entities/analytics-event.entity';
import { RegisterProcessedMediaDto } from './dto/register-processed-media.dto';
import { ProcessedMediaEntity } from './entities/processed-media.entity';

@Injectable()
export class ProcessedMediaService {
  constructor(
    @InjectRepository(ProcessedMediaEntity)
    private readonly repo: Repository<ProcessedMediaEntity>,
    @InjectRepository(AnalyticsEventEntity)
    private readonly eventsRepo: Repository<AnalyticsEventEntity>,
  ) {}

  async listMine(userId: string, limit = 60) {
    const take = Math.min(Math.max(limit, 1), 100);
    return this.repo.find({
      where: { userId },
      order: { createdAt: 'DESC' },
      take,
    });
  }

  async register(userId: string, dto: RegisterProcessedMediaDto) {
    const existing = await this.repo.findOne({
      where: { userId, clientId: dto.clientId },
    });
    if (existing) {
      existing.kind = dto.kind;
      existing.source = dto.source;
      existing.engine = dto.engine?.trim() || existing.engine;
      existing.mediaPath = dto.mediaPath;
      existing.thumbnailPath = dto.thumbnailPath?.trim() || existing.thumbnailPath;
      return this.repo.save(existing);
    }

    const row = this.repo.create({
      userId,
      clientId: dto.clientId,
      kind: dto.kind,
      source: dto.source,
      engine: dto.engine?.trim() || null,
      mediaPath: dto.mediaPath,
      thumbnailPath: dto.thumbnailPath?.trim() || null,
    });
    const saved = await this.repo.save(row);
    await this.eventsRepo.save(
      this.eventsRepo.create({
        name:
          dto.kind === 'video'
            ? 'video_processing_completed'
            : 'photo_processing_completed',
        userId,
        source: dto.source,
        properties: {
          clientId: dto.clientId,
          kind: dto.kind,
          engine: dto.engine ?? null,
        },
      }),
    );
    return saved;
  }

  async remove(userId: string, id: string) {
    const row = await this.repo.findOne({ where: { id, userId } });
    if (!row) {
      throw new NotFoundException('Processed media not found');
    }
    await this.repo.remove(row);
    return { ok: true };
  }
}
