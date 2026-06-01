import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { GroupTripEntity } from './group-trip.entity';

@Entity('group_trip_members')
export class GroupTripMemberEntity {
  @PrimaryColumn({ type: 'uuid', name: 'tripId' })
  tripId: string;

  @PrimaryColumn({ type: 'uuid', name: 'userId' })
  userId: string;

  @Column({ type: 'varchar', length: 20, default: 'member' })
  role: string;

  @Column({ type: 'timestamp', name: 'joinedAt', default: () => 'CURRENT_TIMESTAMP' })
  joinedAt: Date;

  @ManyToOne(() => GroupTripEntity, (t) => t.members, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'tripId' })
  trip?: GroupTripEntity;
}
