import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { DiveLogModerationStatus } from '../../common/statuses';

@Entity('dive_logs')
@Index(['userId'])
@Index(['date'])
export class DiveLogEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @Column({ type: 'uuid', nullable: true })
  diveSiteId: string | null;

  @Column({ type: 'date' })
  date: string;

  @Column({ type: 'timestamptz', nullable: true })
  startTime: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  endTime: Date | null;

  @Column({ type: 'int' })
  duration: number;

  @Column({ type: 'double precision' })
  maxDepth: number;

  @Column({ type: 'double precision', nullable: true })
  averageDepth: number | null;

  @Column({ type: 'double precision', nullable: true })
  waterTemperature: number | null;

  @Column({ type: 'double precision', nullable: true })
  visibility: number | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  current: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  diveType: string | null;

  @Column({ type: 'text', nullable: true })
  notes: string | null;

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  photoUrls: string[];

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  videoUrls: string[];

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  fishSpecies: string[];

  @Column({ type: 'boolean', nullable: true })
  isPublished: boolean | null;

  @Column({
    type: 'varchar',
    length: 32,
    default: DiveLogModerationStatus.PUBLISHED,
  })
  @Index()
  moderationStatus: string;

  @Column({ type: 'timestamp', nullable: true })
  deletedAt?: Date;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
