import { IsBoolean, IsIn, IsInt, IsObject, IsOptional, IsString, MaxLength, MinLength, Min } from 'class-validator';

export class CreateSubscriptionPlanDto {
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  code: string;

  @IsString()
  @MinLength(1)
  @MaxLength(255)
  name: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsInt()
  @Min(0)
  priceCents: number;

  @IsOptional()
  @IsString()
  @MaxLength(3)
  currency?: string;

  @IsOptional()
  @IsString()
  @IsIn(['monthly', 'yearly', 'lifetime'])
  billingInterval?: string;

  @IsOptional()
  @IsBoolean()
  active?: boolean;

  @IsOptional()
  @IsObject()
  features?: Record<string, unknown>;
}

export class UpdateSubscriptionPlanDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  name?: string;

  @IsOptional()
  @IsString()
  description?: string | null;

  @IsOptional()
  @IsInt()
  @Min(0)
  priceCents?: number;

  @IsOptional()
  @IsString()
  @MaxLength(3)
  currency?: string;

  @IsOptional()
  @IsString()
  @IsIn(['monthly', 'yearly', 'lifetime'])
  billingInterval?: string;

  @IsOptional()
  @IsBoolean()
  active?: boolean;

  @IsOptional()
  @IsObject()
  features?: Record<string, unknown> | null;
}
