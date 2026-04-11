import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { UserPushDevice } from './entities/user-push-device.entity';

@Injectable()
export class PushService {
  private readonly logger = new Logger(PushService.name);

  constructor(
    @InjectRepository(UserPushDevice)
    private readonly deviceRepository: Repository<UserPushDevice>,
  ) {}

  async registerToken(
    userId: string,
    token: string,
    platform: string,
  ): Promise<void> {
    const t = token.trim();
    if (!t) {
      return;
    }
    await this.deviceRepository.upsert(
      {
        userId,
        token: t,
        platform: platform || 'ios',
      },
      { conflictPaths: ['userId', 'token'] },
    );
  }

  /**
   * Stub: wire APNs / FCM later. Logs and lists device count for diagnostics.
   */
  async notifyUsers(
    userIds: string[],
    title: string,
    body: string,
  ): Promise<void> {
    const unique = [...new Set(userIds)].filter(Boolean);
    if (!unique.length) {
      return;
    }
    const devices = await this.deviceRepository.find({
      where: { userId: In(unique) },
    });
    this.logger.log(
      `Push (stub): "${title}" — ${body} → ${devices.length} device(s) for ${unique.length} user(s)`,
    );
  }
}
