import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import {
  RiskLevel,
  UserAccountStatus,
  VerificationStatus,
} from '../../common/statuses';

@Entity('users')
@Index(['email'])
@Index(['role'])
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ unique: true })
  email: string;

  @Column()
  password: string;

  @Column()
  firstName: string;

  @Column()
  lastName: string;

  @Column({ nullable: true })
  avatarUrl?: string;

  @Column({ nullable: true })
  phone?: string;

  @Column({ type: 'date', nullable: true })
  dateOfBirth?: Date;

  @Column({ default: 'DIVER_BASIC' })
  role: string;

  @Column({
    type: 'varchar',
    length: 32,
    default: UserAccountStatus.ACTIVE,
  })
  @Index()
  accountStatus: string;

  @Column({
    type: 'varchar',
    length: 32,
    default: VerificationStatus.UNVERIFIED,
  })
  @Index()
  verificationStatus: string;

  @Column({
    type: 'varchar',
    length: 16,
    default: RiskLevel.NORMAL,
  })
  @Index()
  riskLevel: string;

  @Column({ nullable: true })
  subscriptionTier?: string;

  @Column({ type: 'timestamp', nullable: true })
  subscriptionExpiresAt?: Date;

  @Column({ default: 0 })
  totalDives: number;

  @Column({ default: 0 })
  totalDiveTime: number;

  @Column({ type: 'float', nullable: true })
  maxDepth?: number;

  @Column({ default: 'en' })
  language: string;

  @Column({ nullable: true })
  countryCode?: string;

  /** Client-managed diver profile (onboarding, privacy, interests). */
  @Column({ name: 'diver_profile', type: 'jsonb', nullable: true })
  diverProfile?: Record<string, unknown> | null;

  /**
   * Unique public @handle (stored without @, lowercase, [a-z0-9_]{3,30}).
   * Mirrors `diver_profile.username` when set.
   */
  @Column({ type: 'varchar', length: 32, nullable: true, unique: true })
  username?: string | null;

  @Column({ default: 'UTC' })
  timezone: string;

  @Column({ default: false })
  emailVerified: boolean;

  @Column({ default: false })
  phoneVerified: boolean;

  /** После выдачи временного пароля партнёру — обязательная смена в приложении. */
  @Column({ type: 'boolean', default: false })
  mustChangePassword: boolean;

  @Column({ type: 'timestamp', nullable: true })
  lastLogin?: Date;

  @Column({ nullable: true })
  passwordResetCode?: string;

  @Column({ type: 'timestamp', nullable: true })
  passwordResetExpires?: Date;

  @Column({ default: false })
  shareLogbook: boolean;

  /** Текст «о себе» для карточки инструктора в каталоге (редактирует админ центра). */
  @Column({ type: 'text', nullable: true })
  bio?: string | null;

  /** TOTP secret для входа в веб-админку (не отдавать клиенту). */
  @Column({ type: 'varchar', length: 64, nullable: true })
  adminTotpSecret?: string | null;

  @Column({ type: 'boolean', default: false })
  adminTotpEnabled: boolean;

  /** Apple `sub` из identity token (стабильный идентификатор аккаунта). */
  @Column({ type: 'varchar', length: 255, nullable: true, unique: true })
  appleSub?: string | null;

  /** Google `sub` из verified id_token. */
  @Column({ type: 'varchar', length: 255, nullable: true, unique: true })
  googleSub?: string | null;

  @Column({ type: 'uuid', nullable: true })
  mergedIntoUserId?: string;

  @Column({ type: 'timestamp', nullable: true })
  deletedAt?: Date;

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
