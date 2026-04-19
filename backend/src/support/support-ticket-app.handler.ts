import { AdminSupportTicketsService } from '../admin/admin-support-tickets.service';
import { PublicCreateSupportTicketDto } from './dto/public-create-support-ticket.dto';

/** Used by `POST /api/support/tickets`, legacy `/api/v1/...`, and `POST /api/chat/support/tickets`. */
export async function executePublicSupportTicketCreate(
  tickets: AdminSupportTicketsService,
  req: { user: { sub: string; email?: string } },
  dto: PublicCreateSupportTicketDto,
) {
  const meta = dto.metadata
    ? (JSON.parse(JSON.stringify(dto.metadata)) as Record<string, unknown>)
    : null;
  return tickets.createFromAppUser(req.user.sub, req.user.email, {
    subject: dto.subject,
    body: dto.body,
    category: dto.category,
    conversationId: dto.conversationId,
    metadata: meta,
  });
}
