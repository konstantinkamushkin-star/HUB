import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { User } from '../../users/entities/user.entity';

@Entity('buddy_searches')
@Index(['userId'])
@Index(['status'])
export class BuddySearch {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  /** Place only — e.g. Phuket. Not linked to trips. */
  @Column({ type: 'varchar', length: 200 })
  place: string;

  @Column({ type: 'date' })
  dateFrom: string;

  @Column({ type: 'date' })
  dateTo: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  certificationLevel: string | null;

  @Column({ type: 'int', nullable: true })
  diveCount: number | null;

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  languages: string[];

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  interests: string[];

  @Column({ type: 'varchar', length: 20, default: 'open' })
  status: string;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
