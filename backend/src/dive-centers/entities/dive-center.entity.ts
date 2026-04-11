import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { Point } from 'geojson';
import { DiveCenterStatus, VerificationStatus } from '../../common/statuses';

@Entity('dive_centers')
export class DiveCenterEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 255 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ type: 'jsonb', nullable: true })
  localized_description: Record<string, string>;

  // PostGIS geography column
  @Column({
    type: 'geography',
    spatialFeatureType: 'Point',
    srid: 4326,
  })
  @Index({ spatial: true })
  location: Point;

  // Regular columns for latitude/longitude (will be set by trigger)
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

  @Column({ type: 'jsonb', nullable: true })
  social_media: Record<string, string>;

  // Services and features
  @Column({ type: 'text', array: true, default: '{}' })
  services: string[];

  @Column({ type: 'varchar', length: 50, nullable: true })
  certification_agency: string;

  @Column({ type: 'text', array: true, default: '{}' })
  languages: string[];

  @Column({ type: 'boolean', default: false })
  nitrox_available: boolean;

  @Column({ type: 'decimal', precision: 10, scale: 2, nullable: true })
  price_from: number;

  // Operating hours (stored as JSONB)
  @Column({ type: 'jsonb', nullable: true })
  operating_hours: Record<string, any>;

  // Media
  @Column({ type: 'text', array: true, default: '{}' })
  photo_urls: string[];

  @Column({ type: 'text', array: true, default: '{}' })
  video_urls: string[];

  // Rating and reviews
  @Column({ type: 'decimal', precision: 3, scale: 2, default: 0 })
  @Index()
  average_rating: number;

  @Column({ type: 'integer', default: 0 })
  @Index()
  review_count: number;

  @Column({ type: 'text', nullable: true })
  ai_summary: string;

  // Relations
  @Column({ type: 'uuid', array: true, default: '{}' })
  affiliated_sites: string[];

  @Column({ type: 'uuid', array: true, default: '{}' })
  instructor_ids: string[];

  @Column({ type: 'uuid', nullable: true })
  @Index()
  owner_id: string | null;

  // Status
  @Column({ type: 'boolean', default: true })
  @Index()
  is_active: boolean;

  @Column({
    type: 'varchar',
    length: 32,
    default: DiveCenterStatus.PENDING,
  })
  @Index()
  status: string;

  @Column({
    type: 'varchar',
    length: 32,
    default: VerificationStatus.UNVERIFIED,
  })
  @Index()
  verification_status: string;

  @Column({ type: 'timestamp', nullable: true })
  deleted_at?: Date;

  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;
}
