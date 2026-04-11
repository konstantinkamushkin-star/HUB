import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';
import { Point } from 'geojson';
import { DiveSiteStatus } from '../../common/statuses';

@Entity('dive_sites')
export class DiveSiteEntity {
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
  region: string;

  @Column({ type: 'text', nullable: true })
  address: string;

  @Column({ type: 'text', array: true, default: '{}' })
  site_types: string[];

  @Column({ type: 'integer', default: 1 })
  @Index()
  difficulty_level: number;

  @Column({ type: 'double precision', nullable: true })
  depth_min: number;

  @Column({ type: 'double precision', nullable: true })
  depth_max: number;

  @Column({ type: 'double precision', nullable: true })
  water_temp_min: number;

  @Column({ type: 'double precision', nullable: true })
  water_temp_max: number;

  @Column({ type: 'jsonb', nullable: true })
  seasonality: Record<string, boolean>;

  @Column({ type: 'text', array: true, default: '{}' })
  access_type: string[];

  @Column({ type: 'decimal', precision: 10, scale: 2, nullable: true })
  price_from: number;

  @Column({ type: 'decimal', precision: 3, scale: 2, default: 0 })
  @Index()
  average_rating: number;

  @Column({ type: 'integer', default: 0 })
  @Index()
  review_count: number;

  @Column({ type: 'text', array: true, default: '{}' })
  photo_urls: string[];

  @Column({ type: 'text', array: true, default: '{}' })
  video_urls: string[];

  @Column({ type: 'text', array: true, default: '{}' })
  marine_life: string[];

  @Column({ type: 'boolean', default: true })
  @Index()
  is_active: boolean;

  @Column({
    type: 'varchar',
    length: 32,
    default: DiveSiteStatus.PENDING,
  })
  @Index()
  status: string;

  @Column({ type: 'timestamp', nullable: true })
  deleted_at?: Date;

  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;

  @Column({ type: 'text', nullable: true })
  ai_summary: string;

  @Column({ type: 'uuid', array: true, default: '{}' })
  affiliated_centers: string[];
}
