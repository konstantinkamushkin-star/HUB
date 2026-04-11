import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { ReportPriority, ReportStatus } from '../../common/statuses';

@Entity('admin_reports')
@Index(['status'])
@Index(['priority'])
@Index(['targetType', 'targetId'])
@Index(['createdAt'])
export class AdminReportEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 64 })
  targetType: string;

  @Column({ type: 'varchar', length: 128 })
  targetId: string;

  @Column({ type: 'uuid', nullable: true })
  reporterUserId: string | null;

  @Column({ type: 'varchar', length: 128, nullable: true })
  reasonCode: string | null;

  @Column({ type: 'text', nullable: true })
  message: string | null;

  @Column({ type: 'varchar', length: 32, default: ReportStatus.NEW })
  status: string;

  @Column({ type: 'varchar', length: 16, default: ReportPriority.NORMAL })
  priority: string;

  @Column({ type: 'uuid', nullable: true })
  handledByAdminId: string | null;

  @Column({ type: 'text', nullable: true })
  resolution: string | null;

  @Column({ type: 'jsonb', nullable: true })
  history: Record<string, unknown>[] | null;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
