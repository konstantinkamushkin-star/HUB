import { Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { IncomingMessage } from 'http';
import { Repository } from 'typeorm';
import { WebSocket, Server } from 'ws';
import { ChatParticipant } from './entities/chat-participant.entity';

@Injectable()
@WebSocketGateway({ path: '/ws/chat', transports: ['websocket'] })
export class ChatGateway implements OnGatewayConnection, OnGatewayDisconnect {
  private readonly logger = new Logger(ChatGateway.name);

  @WebSocketServer()
  server: Server;

  private readonly socketsByUser = new Map<string, Set<WebSocket>>();

  constructor(
    private readonly jwt: JwtService,
    @InjectRepository(ChatParticipant)
    private readonly participantRepository: Repository<ChatParticipant>,
  ) {}

  @OnEvent('chat.message')
  handleChatMessage(evt: {
    conversationId: string;
    message: Record<string, unknown>;
  }): void {
    void this.broadcastToConversationUsers(evt.conversationId, evt.message);
  }

  handleConnection(client: WebSocket, req: IncomingMessage): void {
    try {
      const host = req.headers.host || '127.0.0.1';
      const url = new URL(req.url || '/', `http://${host}`);
      const token = url.searchParams.get('token');
      if (!token) {
        client.close(4001, 'missing token');
        return;
      }
      const payload = this.jwt.verify<{ sub: string }>(token);
      const userId = payload.sub;
      (client as unknown as { userId?: string }).userId = userId;
      let set = this.socketsByUser.get(userId);
      if (!set) {
        set = new Set();
        this.socketsByUser.set(userId, set);
      }
      set.add(client);
    } catch (e) {
      this.logger.warn(`ws auth failed: ${e}`);
      client.close(4001, 'unauthorized');
    }
  }

  handleDisconnect(client: WebSocket): void {
    const userId = (client as unknown as { userId?: string }).userId;
    if (!userId) {
      return;
    }
    const set = this.socketsByUser.get(userId);
    if (!set) {
      return;
    }
    set.delete(client);
    if (set.size === 0) {
      this.socketsByUser.delete(userId);
    }
  }

  private async broadcastToConversationUsers(
    conversationId: string,
    message: Record<string, unknown>,
  ): Promise<void> {
    const parts = await this.participantRepository.find({
      where: { conversationId },
    });
    const payload = JSON.stringify({ type: 'chat.message', message });
    for (const p of parts) {
      if (p.participantType !== 'user') {
        continue;
      }
      const set = this.socketsByUser.get(p.participantId);
      if (!set) {
        continue;
      }
      for (const ws of set) {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(payload);
        }
      }
    }
  }
}
