import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators/public.decorator';
import { WebhooksService } from './webhooks.service';

@ApiTags('webhooks')
@Controller('webhooks')
export class StripeWebhookController {
  constructor(private readonly webhooks: WebhooksService) {}

  @Post('stripe')
  @Public()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Stripe webhook stub (stores raw JSON; verify signature in production)',
  })
  async stripe(@Body() body: Record<string, unknown>) {
    await this.webhooks.recordStripePayload(
      body && typeof body === 'object' ? body : {},
    );
    return { received: true };
  }
}
