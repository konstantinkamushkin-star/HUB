import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('admin_data_jobs')
@Index(['type'])
@Index(['status'])
@Index(['createdAt'])
export class AdminDataJobEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 32 })
  type: string; // import | export

  @Column({ type: 'varchar', length: 32 })
  format: string; // csv | json | xlsx | pdf | backup

  @Column({ type: 'varchar', length: 64 })
  targetType: string; // users | centers | sites | logs | ...

  @Column({ type: 'varchar', length: 32, default: 'queued' })
  status: string; // queued | processing | completed | failed

  @Column({ type: 'jsonb', nullable: true })
  filters: Record<string, unknown> | null;

  @Column({ type: 'jsonb', nullable: true })
  resultMeta: Record<string, unknown> | null;

  @Column({ type: 'uuid', nullable: true })
  createdByAdminId: string | null;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
