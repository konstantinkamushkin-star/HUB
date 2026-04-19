import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

export type DiveSiteContributionType = 'correction' | 'new_site';
export type DiveSiteContributionStatus = 'pending' | 'approved' | 'rejected';

@Entity('dive_site_contributions')
export class DiveSiteContributionEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'contribution_type', type: 'varchar', length: 32 })
  @Index()
  contribution_type: DiveSiteContributionType;

  @Column({ name: 'dive_site_id', type: 'uuid', nullable: true })
  dive_site_id: string | null;

  @Column({ name: 'submitter_user_id', type: 'uuid' })
  @Index()
  submitter_user_id: string;

  @Column({ name: 'proposed_data', type: 'jsonb', default: {} })
  proposed_data: Record<string, unknown>;

  @Column({ type: 'text', nullable: true })
  message: string | null;

  @Column({ type: 'varchar', length: 32, default: 'pending' })
  @Index()
  status: DiveSiteContributionStatus;

  @Column({ name: 'reviewed_by', type: 'uuid', nullable: true })
  reviewed_by: string | null;

  @Column({ name: 'reviewed_at', type: 'timestamptz', nullable: true })
  reviewed_at: Date | null;

  @Column({ name: 'rejection_reason', type: 'text', nullable: true })
  rejection_reason: string | null;

  @CreateDateColumn({ name: 'created_at' })
  created_at: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updated_at: Date;
}
