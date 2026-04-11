import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('admin_support_tickets')
@Index(['status'])
@Index(['createdAt'])
export class AdminSupportTicketEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid', nullable: true, name: 'reporter_user_id' })
  reporterUserId: string | null;

  @Column({ type: 'varchar', length: 255, nullable: true, name: 'reporter_email' })
  reporterEmail: string | null;

  @Column({ type: 'varchar', length: 512 })
  subject: string;

  @Column({ type: 'text' })
  body: string;

  @Column({ type: 'varchar', length: 32, default: 'open' })
  status: string;

  @Column({ type: 'varchar', length: 16, default: 'normal' })
  priority: string;

  @Column({ type: 'uuid', nullable: true, name: 'assigned_admin_id' })
  assignedAdminId: string | null;

  @Column({ type: 'text', nullable: true, name: 'resolution_note' })
  resolutionNote: string | null;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}
