import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { Point } from 'geojson';
import { VerificationStatus } from '../../common/statuses';

@Entity('shops')
export class ShopEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 255 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ type: 'jsonb', nullable: true })
  localized_name: Record<string, string>;

  @Column({ type: 'jsonb', nullable: true })
  localized_description: Record<string, string>;

  @Column({ type: 'varchar', length: 20, default: 'offline' })
  @Index()
  type: string; // 'offline' or 'online'

  @Column({ type: 'text', array: true, default: '{}' })
  brands: string[];

  @Column({ type: 'boolean', default: false })
  service_available: boolean;

  // PostGIS geography column
  @Column({
    type: 'geography',
    spatialFeatureType: 'Point',
    srid: 4326,
    nullable: true,
  })
  @Index({ spatial: true })
  location: Point;

  // Regular columns for latitude/longitude
  @Column({ type: 'double precision', nullable: true })
  latitude: number;

  @Column({ type: 'double precision', nullable: true })
  longitude: number;

  @Column({ type: 'varchar', length: 100, nullable: true })
  country: string;

  @Column({ type: 'varchar', length: 100, nullable: true })
  city: string;

  @Column({ type: 'text', nullable: true })
  address: string;

  // Contact info
  @Column({ type: 'varchar', length: 255, nullable: true })
  email: string;

  @Column({ type: 'varchar', length: 50, nullable: true })
  phone: string;

  @Column({ type: 'varchar', length: 255, nullable: true })
  website: string;

  // Media
  @Column({ type: 'text', array: true, default: '{}' })
  photo_urls: string[];

  // Rating and reviews
  @Column({ type: 'decimal', precision: 3, scale: 2, default: 0 })
  @Index()
  average_rating: number;

  @Column({ type: 'integer', default: 0 })
  @Index()
  review_count: number;

  // Owner/Admin user ID
  @Column({ type: 'uuid', nullable: true })
  @Index()
  owner_id: string;

  // Status
  @Column({ type: 'boolean', default: true })
  @Index()
  is_active: boolean;

  @Column({
    type: 'varchar',
    length: 32,
    default: VerificationStatus.UNVERIFIED,
  })
  @Index()
  verification_status: string;

  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;
}
