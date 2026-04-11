import {
  Entity,
  PrimaryColumn,
  Column,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { ChatConversation } from './chat-conversation.entity';

@Entity('chat_conversation_participants')
@Index(['participantType', 'participantId'])
export class ChatParticipant {
  @PrimaryColumn('uuid', { name: 'conversationId' })
  conversationId: string;

  @PrimaryColumn({ name: 'participantType', type: 'varchar', length: 20 })
  participantType: string;

  @PrimaryColumn('uuid', { name: 'participantId' })
  participantId: string;

  @Column({ name: 'lastReadAt', type: 'timestamptz', nullable: true })
  lastReadAt: Date | null;

  @ManyToOne(() => ChatConversation, (c) => c.participantRecords, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'conversationId' })
  conversation: ChatConversation;
}
