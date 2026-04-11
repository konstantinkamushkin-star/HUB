import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { PaymentWebhookEventEntity } from '../admin/entities/payment-webhook-event.entity';

@Injectable()
export class WebhooksService {
  constructor(
    @InjectRepository(PaymentWebhookEventEntity)
    private readonly repo: Repository<PaymentWebhookEventEntity>,
  ) {}

  async recordStripePayload(payload: Record<string, unknown>) {
    const eventType =
      typeof payload.type === 'string' ? payload.type : null;
    const row = this.repo.create({
      provider: 'stripe',
      eventType,
      payload,
    });
    return this.repo.save(row);
  }
}
