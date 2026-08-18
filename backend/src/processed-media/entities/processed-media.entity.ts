import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  Unique,
} from 'typeorm';
import { User } from '../../users/entities/user.entity';

@Entity('processed_media')
@Unique(['userId', 'clientId'])
@Index(['userId', 'createdAt'])
export class ProcessedMediaEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'varchar', length: 64 })
  clientId: string;

  @Column({ type: 'varchar', length: 16 })
  kind: 'image' | 'video';

  @Column({ type: 'varchar', length: 16 })
  source: 'offline' | 'server';

  @Column({ type: 'varchar', length: 32, nullable: true })
  engine: string | null;

  @Column({ type: 'varchar', length: 512 })
  mediaPath: string;

  @Column({ type: 'varchar', length: 512, nullable: true })
  thumbnailPath: string | null;

  @CreateDateColumn({ name: 'createdAt' })
  createdAt: Date;
}
