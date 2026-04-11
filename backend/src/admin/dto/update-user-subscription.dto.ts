import { IsDateString, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateUserSubscriptionDto {
  @IsOptional()
  @IsString()
  @MaxLength(64)
  subscriptionTier?: string | null;

  @IsOptional()
  @IsDateString()
  subscriptionExpiresAt?: string | null;

  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}
