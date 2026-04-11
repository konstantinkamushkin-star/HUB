import { IsBoolean, IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpsertSystemSettingDto {
  @IsString()
  @MinLength(2)
  @MaxLength(120)
  key: string;

  @IsObject()
  value: Record<string, unknown>;

  @IsOptional()
  @IsBoolean()
  isSensitive?: boolean;

  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}
