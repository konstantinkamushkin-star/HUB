import {
  IsArray,
  IsDateString,
  IsIn,
  IsInt,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

export class CreateBookingDto {
  @IsUUID()
  diveCenterId: string;

  @IsString()
  @MaxLength(120)
  serviceId: string;

  @IsOptional()
  @IsUUID()
  diveSiteId?: string;

  @IsOptional()
  @IsUUID()
  instructorId?: string;

  @IsDateString()
  date: string;

  @IsOptional()
  @IsDateString()
  dateEnd?: string;

  @IsString()
  @Matches(/^\d{2}:\d{2}$/)
  startTime: string;

  @IsArray()
  participants: unknown[];

  @IsOptional()
  @IsArray()
  gearRental?: unknown[];

  @IsObject()
  payment: Record<string, unknown>;

  @IsOptional()
  @IsIn(['pending', 'quoted', 'confirmed', 'completed', 'cancelled', 'refunded'])
  status?:
    | 'pending'
    | 'quoted'
    | 'confirmed'
    | 'completed'
    | 'cancelled'
    | 'refunded';

  @IsOptional()
  @IsString()
  @MaxLength(6000)
  notes?: string;

  @IsOptional()
  @IsIn(['open_water', 'pool'])
  bookingType?: 'open_water' | 'pool';

  @IsOptional()
  @IsIn(['instant', 'manual_approval'])
  requestMode?: 'instant' | 'manual_approval';

  @IsOptional()
  @IsString()
  @MaxLength(120)
  sessionId?: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(200)
  participantsCount?: number;

  @IsOptional()
  @IsObject()
  instructorPreferences?: Record<string, unknown>;

  @IsOptional()
  @IsObject()
  equipmentRental?: Record<string, unknown>;
}
