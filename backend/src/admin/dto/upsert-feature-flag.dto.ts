import { IsBoolean, IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpsertFeatureFlagDto {
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  key: string;

  @IsBoolean()
  enabled: boolean;

  @IsOptional()
  @IsObject()
  rolloutRules?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  description?: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason?: string;
}
