import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
  UseGuards,
  Request,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { AdminSupportTicketsService } from '../admin/admin-support-tickets.service';
import { PublicCreateSupportTicketDto } from '../support/dto/public-create-support-ticket.dto';
import { executePublicSupportTicketCreate } from '../support/support-ticket-app.handler';
import { ChatService } from './chat.service';
import { OpenChatDto } from './dto/open-chat.dto';
import { SendChatMessageDto } from './dto/send-chat-message.dto';
import { OpenContributionSupportChatDto } from './dto/open-contribution-support-chat.dto';
import { OpenAppSupportTopicDto } from './dto/open-app-support-topic.dto';

@ApiTags('chat')
@Controller('chat')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class ChatController {
  constructor(
    private readonly chatService: ChatService,
    private readonly supportTickets: AdminSupportTicketsService,
  ) {}

  @Get('conversations')
  @ApiOperation({ summary: 'List conversations for current user' })
  async listConversations(@Request() req: { user: { sub: string } }) {
    return this.chatService.listConversations(req.user.sub);
  }

  @Post('conversations')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Open or create a conversation' })
  async openConversation(
    @Request() req: { user: { sub: string } },
    @Body() dto: OpenChatDto,
  ) {
    return this.chatService.openConversation(req.user.sub, dto);
  }

  @Post('conversations/contribution-support')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Open support chat for a dive-site contribution (submitter only; pairs with admin).',
  })
  async openContributionSupport(
    @Request() req: { user: { sub: string } },
    @Body() dto: OpenContributionSupportChatDto,
  ) {
    return this.chatService.openContributionSupportChat(
      req.user.sub,
      dto.contributionId,
    );
  }

  @Post('support/topics')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary:
      'Open or create an app support thread (one topic per topicId; omit topicId for a new topic)',
  })
  async openAppSupportTopic(
    @Request() req: { user: { sub: string } },
    @Body() dto: OpenAppSupportTopicDto,
  ) {
    return this.chatService.openAppSupportTopicChat(
      req.user.sub,
      dto.topicId,
      dto.title,
    );
  }

  /** Same as `POST /api/support/tickets` — under `/api/chat` so older API deploys still route feedback. */
  @Post('support/tickets')
  @HttpCode(HttpStatus.CREATED)
  @Throttle({ default: { ttl: 60000, limit: 10 } })
  @ApiOperation({ summary: 'Submit feedback / bug report (authenticated app user)' })
  async createSupportTicketFromApp(
    @Request() req: { user: { sub: string; email?: string } },
    @Body() dto: PublicCreateSupportTicketDto,
  ) {
    return executePublicSupportTicketCreate(this.supportTickets, req, dto);
  }

  @Post('messages')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Send a text message' })
  async sendMessage(
    @Request() req: { user: { sub: string } },
    @Body() dto: SendChatMessageDto,
  ) {
    return this.chatService.sendMessage(req.user.sub, dto);
  }

  @Get(':conversationId/messages')
  @ApiOperation({ summary: 'List messages (newest page first; before=id loads older)' })
  async listMessages(
    @Request() req: { user: { sub: string } },
    @Param('conversationId') conversationId: string,
    @Query('before') before?: string,
    @Query('limit') limitStr?: string,
  ) {
    const limit = limitStr ? parseInt(limitStr, 10) : undefined;
    return this.chatService.listMessages(req.user.sub, conversationId, {
      beforeMessageId: before || undefined,
      limit: Number.isFinite(limit) ? limit : undefined,
      markRead: !before,
    });
  }
}
