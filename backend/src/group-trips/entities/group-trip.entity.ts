import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  OneToMany,
} from 'typeorm';
import { GroupTripMemberEntity } from './group-trip-member.entity';

@Entity('group_trips')
export class GroupTripEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 255 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true })
  destination: string | null;

  @Column({ type: 'uuid', name: 'organizerId' })
  organizerId: string;

  @Column({ type: 'uuid', name: 'chatConversationId', nullable: true })
  chatConversationId: string | null;

  @Column({ type: 'date', nullable: true })
  startDate: string | null;

  @Column({ type: 'date', nullable: true })
  endDate: string | null;

  @OneToMany(() => GroupTripMemberEntity, (m) => m.trip)
  members?: GroupTripMemberEntity[];

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
