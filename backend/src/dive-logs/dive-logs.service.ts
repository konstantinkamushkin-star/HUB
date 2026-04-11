import { ForbiddenException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { DiveLogEntity } from './entities/dive-log.entity';
import { CreateDiveLogDto } from './dto/create-dive-log.dto';

@Injectable()
export class DiveLogsService {
  constructor(
    @InjectRepository(DiveLogEntity)
    private readonly diveLogRepository: Repository<DiveLogEntity>,
  ) {}

  async create(userId: string, dto: CreateDiveLogDto): Promise<DiveLogEntity> {
    const row = this.diveLogRepository.create({
      userId,
      diveSiteId: dto.diveSiteId ?? null,
      date: dto.date,
      startTime: dto.startTime ? new Date(dto.startTime) : null,
      endTime: dto.endTime ? new Date(dto.endTime) : null,
      duration: dto.duration,
      maxDepth: dto.maxDepth,
      averageDepth: dto.averageDepth ?? null,
      waterTemperature: dto.waterTemperature ?? null,
      visibility: dto.visibility ?? null,
      current: dto.current ?? null,
      diveType: dto.diveType ?? null,
      notes: dto.notes ?? null,
      photoUrls: dto.photoUrls ?? [],
      videoUrls: dto.videoUrls ?? [],
      fishSpecies: dto.fishSpecies ?? [],
      isPublished: dto.isPublished ?? null,
    });
    return this.diveLogRepository.save(row);
  }

  async listForUser(requesterId: string, requestedUserId?: string) {
    const userId = requestedUserId || requesterId;
    if (userId !== requesterId) {
      throw new ForbiddenException('Not allowed to view other user dive logs');
    }
    return this.diveLogRepository.find({
      where: { userId },
      order: { date: 'DESC', createdAt: 'DESC' },
    });
  }

  async listPublicByDiveSite(diveSiteId?: string, limit = 20) {
    if (!diveSiteId) {
      return [];
    }
    const lim = Math.min(Math.max(limit, 1), 100);
    return this.diveLogRepository.find({
      where: { diveSiteId },
      order: { date: 'DESC', createdAt: 'DESC' },
      take: lim,
    });
  }
}
