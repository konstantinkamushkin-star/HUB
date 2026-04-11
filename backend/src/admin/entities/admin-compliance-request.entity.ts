import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('admin_compliance_requests')
@Index(['userId'])
@Index(['type'])
@Index(['status'])
@Index(['createdAt'])
export class AdminComplianceRequestEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @Column({ type: 'varchar', length: 32 })
  type: string; // export_data | delete_data

  @Column({ type: 'varchar', length: 32, default: 'pending' })
  status: string; // pending | in_review | completed | rejected

  @Column({ type: 'text', nullable: true })
  reason: string | null;

  @Column({ type: 'jsonb', nullable: true })
  payload: Record<string, unknown> | null;

  @Column({ type: 'uuid', nullable: true })
  handledByAdminId: string | null;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
