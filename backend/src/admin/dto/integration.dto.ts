import { IsBoolean, IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpsertIntegrationDto {
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  key: string;

  @IsString()
  @MinLength(1)
  @MaxLength(255)
  displayName: string;

  @IsOptional()
  @IsBoolean()
  enabled?: boolean;

  @IsOptional()
  @IsObject()
  config?: Record<string, unknown>;
}

export class PatchIntegrationDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  displayName?: string;

  @IsOptional()
  @IsBoolean()
  enabled?: boolean;

  @IsOptional()
  @IsObject()
  config?: Record<string, unknown> | null;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  lastCheckStatus?: string | null;
}

/** Body for dangerous integration POST (requires `reason` for audit guard). */
export class UpsertIntegrationDangerousDto extends UpsertIntegrationDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}

export class PatchIntegrationDangerousDto extends PatchIntegrationDto {
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}
