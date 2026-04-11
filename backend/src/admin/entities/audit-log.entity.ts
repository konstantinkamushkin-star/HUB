import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity('admin_audit_logs')
@Index(['adminId'])
@Index(['action'])
@Index(['targetType', 'targetId'])
@Index(['createdAt'])
export class AdminAuditLogEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid', nullable: true })
  adminId: string | null;

  @Column({ type: 'varchar', length: 128 })
  action: string;

  @Column({ type: 'varchar', length: 64, nullable: true })
  targetType: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  targetId: string | null;

  @Column({ type: 'jsonb', nullable: true })
  before: Record<string, unknown> | null;

  @Column({ type: 'jsonb', nullable: true })
  after: Record<string, unknown> | null;

  @Column({ type: 'varchar', length: 64, nullable: true })
  ip: string | null;

  @Column({ type: 'varchar', length: 256, nullable: true })
  device: string | null;

  @Column({ type: 'varchar', length: 32, default: 'success' })
  outcome: string;

  @Column({ type: 'text', nullable: true })
  reason: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  correlationId: string | null;

  @CreateDateColumn()
  createdAt: Date;
}
