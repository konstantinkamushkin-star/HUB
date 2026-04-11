import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminNotificationCampaignEntity } from './entities/notification-campaign.entity';
import { CreateNotificationCampaignDto } from './dto/create-notification-campaign.dto';
import { AuditLogService } from './audit-log.service';
import { PushService } from '../push/push.service';
import { User } from '../users/entities/user.entity';

@Injectable()
export class AdminNotificationsService {
  constructor(
    @InjectRepository(AdminNotificationCampaignEntity)
    private readonly campaignsRepo: Repository<AdminNotificationCampaignEntity>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    private readonly auditLogService: AuditLogService,
    private readonly pushService: PushService,
  ) {}

  list() {
    return this.campaignsRepo.find({ order: { createdAt: 'DESC' }, take: 200 });
  }

  async createCampaign(dto: CreateNotificationCampaignDto, actor: any) {
    const row = this.campaignsRepo.create({
      channel: dto.channel,
      title: dto.title,
      body: dto.body,
      audience: dto.audience ?? null,
      status: 'queued',
      createdByAdminId: actor?.adminId ?? null,
      recipientCount: 0,
    });
    const saved = await this.campaignsRepo.save(row);

    // Initial broadcast strategy: all active users for push channel.
    if (dto.channel === 'push') {
      const users = await this.usersRepo.find({
        where: { accountStatus: 'active' },
        select: ['id'],
        take: 10000,
      });
      const ids = users.map((u) => u.id);
      await this.pushService.notifyUsers(ids, dto.title, dto.body);
      saved.recipientCount = ids.length;
      saved.status = 'sent';
      await this.campaignsRepo.save(saved);
    } else {
      saved.status = 'scheduled';
      await this.campaignsRepo.save(saved);
    }

    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.notification_campaign.create',
      targetType: 'notification_campaign',
      targetId: saved.id,
      before: null,
      after: {
        channel: saved.channel,
        title: saved.title,
        status: saved.status,
        recipientCount: saved.recipientCount,
      },
      reason: dto.reason,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }
}
