import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

export type CertificationVerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

@Entity('user_certifications')
@Index(['userId'])
export class UserCertificationEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid', name: 'user_id' })
  userId: string;

  @Column({ type: 'varchar', length: 128 })
  agency: string;

  @Column({ type: 'varchar', length: 256 })
  level: string;

  @Column({ type: 'text', nullable: true, name: 'card_image_url' })
  cardImageUrl: string | null;

  @Column({ type: 'timestamptz', nullable: true, name: 'issue_date' })
  issueDate: Date | null;

  @Column({ type: 'varchar', length: 128, nullable: true, name: 'instructor_number' })
  instructorNumber: string | null;

  @Column({ type: 'varchar', length: 64, nullable: true, name: 'certificate_number' })
  certificateNumber: string | null;

  @Column({ type: 'varchar', length: 32, default: 'PENDING', name: 'verification_status' })
  verificationStatus: CertificationVerificationStatus;

  @CreateDateColumn({ type: 'timestamptz', name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz', name: 'updated_at' })
  updatedAt: Date;
}
