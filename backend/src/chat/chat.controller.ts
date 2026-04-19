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
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { ChatService } from './chat.service';
import { OpenChatDto } from './dto/open-chat.dto';
import { SendChatMessageDto } from './dto/send-chat-message.dto';
import { OpenContributionSupportChatDto } from './dto/open-contribution-support-chat.dto';

@ApiTags('chat')
@Controller('chat')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class ChatController {
  constructor(private readonly chatService: ChatService) {}

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
