import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { ChatConversation } from './chat-conversation.entity';

@Entity('chat_messages')
@Index(['conversationId', 'createdAt'])
export class ChatMessageEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'conversationId', type: 'uuid' })
  conversationId: string;

  @ManyToOne(() => ChatConversation, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'conversationId' })
  conversation: ChatConversation;

  @Column({ name: 'senderType', type: 'varchar', length: 20 })
  senderType: string;

  @Column({ name: 'senderId', type: 'uuid' })
  senderId: string;

  @Column({ type: 'text' })
  content: string;

  @Column({ name: 'messageType', type: 'varchar', length: 20, default: 'text' })
  messageType: string;

  @Column({ type: 'jsonb', nullable: true })
  attachments: Array<{
    type: string;
    url: string;
    thumbnailURL?: string;
    duration?: number;
  }> | null;

  @CreateDateColumn()
  createdAt: Date;
}
