import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('analytics_events')
@Index(['name'])
@Index(['createdAt'])
export class AnalyticsEventEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 128 })
  name: string;

  @Column({ type: 'jsonb', nullable: true })
  properties: Record<string, unknown> | null;

  @Column({ type: 'uuid', nullable: true, name: 'userId' })
  userId: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true, name: 'sessionId' })
  sessionId: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  source: string | null;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}
