import {
  IsArray,
  IsBoolean,
  IsInt,
  IsNumber,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

export class CreateCenterServiceDto {
  @IsUUID()
  diveCenterId: string;

  @IsString()
  @MaxLength(200)
  name: string;

  @IsOptional()
  @IsString()
  @MaxLength(6000)
  description?: string;

  @IsOptional()
  @IsString()
  @MaxLength(40)
  serviceType?: string;

  @IsNumber()
  @Min(0)
  basePriceAmount: number;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  currency?: string;

  @IsOptional()
  @IsString()
  @MaxLength(24)
  pricingUnit?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(20000)
  durationMinutes?: number;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(2000)
  maxParticipants?: number;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  requirements?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  includedItems?: string[];

  @IsOptional()
  @IsObject()
  pricingRules?: Record<string, unknown>;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(100)
  ownGearDiscountPercent?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(1000)
  groupDiscountThreshold?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(100)
  groupDiscountPercent?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  nightDiveSurchargeAmount?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  privateInstructorSurchargeAmount?: number;

  @IsOptional()
  @IsBoolean()
  isActive?: boolean;
}
