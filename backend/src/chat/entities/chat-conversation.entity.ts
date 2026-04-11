import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  OneToMany,
} from 'typeorm';
import { ChatParticipant } from './chat-participant.entity';

@Entity('chat_conversations')
export class ChatConversation {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 30 })
  kind: string;

  @Column({ name: 'canonicalKey', type: 'varchar', length: 220, unique: true })
  canonicalKey: string;

  @Column({ name: 'diveCenterId', type: 'uuid', nullable: true })
  diveCenterId: string | null;

  @Column({ name: 'shopId', type: 'uuid', nullable: true })
  shopId: string | null;

  @Column({ name: 'bookingId', type: 'uuid', nullable: true })
  bookingId: string | null;

  @OneToMany(() => ChatParticipant, (p) => p.conversation)
  participantRecords: ChatParticipant[];

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
