import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { AdminSupportTicketsService } from '../admin/admin-support-tickets.service';
import { PublicCreateSupportTicketDto } from './dto/public-create-support-ticket.dto';
import { executePublicSupportTicketCreate } from './support-ticket-app.handler';

@ApiTags('support')
@Controller('support/tickets')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class PublicSupportTicketsController {
  constructor(private readonly tickets: AdminSupportTicketsService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @Throttle({ default: { ttl: 60000, limit: 10 } })
  @ApiOperation({ summary: 'Submit feedback / bug report (authenticated app user)' })
  async create(
    @Request() req: { user: { sub: string; email?: string } },
    @Body() dto: PublicCreateSupportTicketDto,
  ) {
    return executePublicSupportTicketCreate(this.tickets, req, dto);
  }
}

/** @deprecated Prefer `POST /api/support/tickets` — kept for older clients. */
@ApiTags('support')
@Controller('v1/support/tickets')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class PublicSupportTicketsV1Controller {
  constructor(private readonly tickets: AdminSupportTicketsService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @Throttle({ default: { ttl: 60000, limit: 10 } })
  @ApiOperation({
    summary:
      'Submit feedback / bug report (legacy path; same as POST /api/support/tickets)',
  })
  async create(
    @Request() req: { user: { sub: string; email?: string } },
    @Body() dto: PublicCreateSupportTicketDto,
  ) {
    return executePublicSupportTicketCreate(this.tickets, req, dto);
  }
}
