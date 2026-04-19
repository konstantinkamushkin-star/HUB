import {
  BadRequestException,
  Injectable,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Stripe from 'stripe';

/**
 * Optional Stripe PaymentIntent for online booking deposits (same contract for iOS/Android).
 * Requires STRIPE_SECRET_KEY. Without it, clients should use on-site / manual flows.
 */
@Injectable()
export class BookingsStripeService {
  private readonly stripe: { paymentIntents: { create: (p: Record<string, unknown>) => Promise<{ client_secret: string | null }> } } | null;

  constructor(private readonly config: ConfigService) {
    const secret = this.config.get<string>('STRIPE_SECRET_KEY')?.trim();
    this.stripe = secret ? (new Stripe(secret) as any) : null;
  }

  isEnabled(): boolean {
    return this.stripe != null;
  }

  async createPaymentIntent(
    amount: number,
    currency: string,
    diveCenterId: string,
  ): Promise<{ clientSecret: string; publishableKey: string | null }> {
    if (!this.stripe) {
      throw new ServiceUnavailableException(
        'Stripe is not configured (set STRIPE_SECRET_KEY on the server)',
      );
    }
    const cur = currency.trim().toLowerCase();
    if (!/^[a-z]{3}$/.test(cur)) {
      throw new BadRequestException('currency must be a 3-letter ISO code');
    }
    const unitAmount = Math.round(amount * 100);
    if (!Number.isFinite(unitAmount) || unitAmount < 0) {
      throw new BadRequestException('invalid amount');
    }
    const intent = await this.stripe.paymentIntents.create({
      amount: unitAmount,
      currency: cur,
      metadata: { diveCenterId },
      automatic_payment_methods: { enabled: true },
    });
    const publishableKey =
      this.config.get<string>('STRIPE_PUBLISHABLE_KEY')?.trim() || null;
    if (!intent.client_secret) {
      throw new ServiceUnavailableException('Stripe did not return client_secret');
    }
    return { clientSecret: intent.client_secret, publishableKey };
  }
}
