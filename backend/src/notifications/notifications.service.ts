import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UserNotificationEntity } from './entities/user-notification.entity';

export type CreateUserNotificationInput = {
  type: string;
  title: string;
  message: string;
  icon?: string | null;
  actionUrl?: string | null;
};

export type NotificationApiItem = {
  id: string;
  type: string;
  title: string;
  message: string;
  icon: string;
  isRead: boolean;
  createdAt: string;
  actionURL: string | null;
};

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    @InjectRepository(UserNotificationEntity)
    private readonly repo: Repository<UserNotificationEntity>,
  ) {}

  /**
   * In-app inbox row. Failures are logged only so core flows (friend request, chat) still succeed.
   */
  async createForUser(
    userId: string,
    payload: CreateUserNotificationInput,
  ): Promise<void> {
    try {
      const type = (payload.type ?? 'system').trim().slice(0, 32) || 'system';
      const title = (payload.title ?? '').trim().slice(0, 255);
      const message = (payload.message ?? '').trim().slice(0, 8000);
      if (!title || !message) {
        return;
      }
      const row = this.repo.create({
        userId,
        type,
        title,
        message,
        icon: payload.icon?.trim()?.slice(0, 64) || 'bell',
        isRead: false,
        actionUrl: payload.actionUrl?.trim()?.slice(0, 2000) || null,
      });
      await this.repo.save(row);
    } catch (e) {
      this.logger.warn(
        `createForUser failed for ${userId}: ${e instanceof Error ? e.message : String(e)}`,
      );
    }
  }

  private toApiItem(row: UserNotificationEntity): NotificationApiItem {
    return {
      id: row.id,
      type: row.type,
      title: row.title,
      message: row.message,
      icon: row.icon?.trim() || 'bell',
      isRead: row.isRead,
      createdAt: row.createdAt.toISOString(),
      actionURL: row.actionUrl,
    };
  }

  async listForUser(userId: string): Promise<{ notifications: NotificationApiItem[] }> {
    const rows = await this.repo.find({
      where: { userId },
      order: { createdAt: 'DESC' },
      take: 200,
    });
    return { notifications: rows.map((r) => this.toApiItem(r)) };
  }

  async markAllRead(userId: string): Promise<void> {
    await this.repo.update({ userId }, { isRead: true });
  }

  async deleteForUser(userId: string, id: string): Promise<void> {
    const res = await this.repo.delete({ id, userId });
    if (!res.affected) {
      throw new NotFoundException('Notification not found');
    }
  }
}
