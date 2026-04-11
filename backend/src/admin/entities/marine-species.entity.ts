import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

@Entity('marine_species')
@Index(['status'])
export class MarineSpeciesEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 255, name: 'scientific_name' })
  scientificName: string;

  @Column({ type: 'varchar', length: 255, name: 'common_name' })
  commonName: string;

  @Column({ type: 'varchar', length: 128, nullable: true })
  family: string | null;

  @Column({ type: 'text', nullable: true })
  description: string | null;

  @Column({ type: 'text', nullable: true, name: 'photo_url' })
  photoUrl: string | null;

  @Column({ type: 'varchar', length: 32, default: 'published' })
  status: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}
