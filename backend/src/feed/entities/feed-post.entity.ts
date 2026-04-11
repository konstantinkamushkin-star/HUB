import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  ManyToOne,
  OneToMany,
  JoinColumn,
  Index,
} from 'typeorm';
import { User } from '../../users/entities/user.entity';
import { FeedPostLike } from './feed-post-like.entity';
import { FeedPostComment } from './feed-post-comment.entity';
import { FeedPostStatus } from '../../common/statuses';

@Entity('feed_posts')
@Index(['userId'])
@Index(['createdAt'])
export class FeedPost {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  @Column({ type: 'varchar', length: 20 })
  type: string;

  @Column({ type: 'text', nullable: true })
  content: string | null;

  @Column({ type: 'uuid', nullable: true })
  diveLogId: string | null;

  @Column({ type: 'jsonb', default: () => "'[]'::jsonb" })
  photos: string[];

  @Column({
    type: 'varchar',
    length: 32,
    default: FeedPostStatus.PUBLISHED,
  })
  @Index()
  moderationStatus: string;

  @Column({ type: 'boolean', default: true })
  commentsEnabled: boolean;

  @Column({ type: 'boolean', default: true })
  likesEnabled: boolean;

  @Column({ type: 'timestamp', nullable: true })
  deletedAt?: Date;

  @OneToMany(() => FeedPostLike, (l) => l.post)
  likes: FeedPostLike[];

  @OneToMany(() => FeedPostComment, (c) => c.post)
  comments: FeedPostComment[];

  @CreateDateColumn()
  createdAt: Date;

  @UpdateDateColumn()
  updatedAt: Date;
}
