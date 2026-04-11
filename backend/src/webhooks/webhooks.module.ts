import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { PaymentWebhookEventEntity } from '../admin/entities/payment-webhook-event.entity';
import { StripeWebhookController } from './stripe-webhook.controller';
import { WebhooksService } from './webhooks.service';

@Module({
  imports: [TypeOrmModule.forFeature([PaymentWebhookEventEntity])],
  controllers: [StripeWebhookController],
  providers: [WebhooksService],
})
export class WebhooksModule {}
