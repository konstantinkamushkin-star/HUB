import {
  IsArray,
  IsBoolean,
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
  MinLength,
} from 'class-validator';

/** Same shape as create (no diveCenterId — organizer is fixed on the trip row). */
export class UpdateTripDto {
  @IsIn(['daily', 'safari'])
  tripType: 'daily' | 'safari';

  @IsString()
  @MinLength(2)
  @MaxLength(100)
  country: string;

  @IsOptional()
  @IsString()
  @MaxLength(100)
  region?: string;

  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  startDate: string;

  @Matches(/^\d{4}-\d{2}-\d{2}$/)
  endDate: string;

  @IsString()
  @MinLength(5)
  @MaxLength(8000)
  description: string;

  @IsInt()
  @Min(1)
  @Max(500)
  totalSpots: number;

  @IsOptional()
  @IsString()
  @MaxLength(100)
  minimumCertificationLevel?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  minimumDives?: number;

  @IsOptional()
  @IsBoolean()
  nitroxAvailable?: boolean;

  @IsOptional()
  @IsBoolean()
  equipmentRentalAvailable?: boolean;

  @IsOptional()
  @IsUUID()
  hotelId?: string | null;

  @IsOptional()
  @IsUUID()
  yachtId?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  hotelLabel?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  yachtLabel?: string | null;

  @IsOptional()
  @IsUUID()
  groupLeaderId?: string | null;

  @IsOptional()
  @IsArray()
  programDays?: unknown[];

  @IsOptional()
  @IsArray()
  additionalExpenses?: unknown[];

  @IsOptional()
  @IsObject()
  priceDetails?: Record<string, unknown>;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  photoUrls?: string[];

  @IsOptional()
  @IsArray()
  @IsUUID('4', { each: true })
  availableCourseIds?: string[];
}
