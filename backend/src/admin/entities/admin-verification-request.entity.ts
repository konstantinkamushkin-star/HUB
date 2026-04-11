import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('admin_verification_requests')
@Index(['targetType', 'targetId'])
@Index(['status'])
@Index(['createdAt'])
export class AdminVerificationRequestEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 64 })
  targetType: string;

  @Column({ type: 'varchar', length: 128 })
  targetId: string;

  @Column({ type: 'varchar', length: 32, default: 'pending' })
  status: string; // pending | verified | rejected | more_info | revoked

  @Column({ type: 'int', default: 1 })
  attemptNumber: number;

  @Column({ type: 'jsonb', nullable: true })
  documents: Record<string, unknown> | null;

  @Column({ type: 'text', nullable: true })
  decisionNote: string | null;

  @Column({ type: 'uuid', nullable: true })
  handledByAdminId: string | null;

  @Column({ type: 'jsonb', nullable: true })
  history: Record<string, unknown>[] | null;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
