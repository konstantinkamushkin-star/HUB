import { IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateVerificationRequestDto {
  @IsString()
  @MinLength(2)
  @MaxLength(64)
  targetType: string;

  @IsString()
  @MinLength(2)
  @MaxLength(128)
  targetId: string;

  @IsOptional()
  @IsObject()
  documents?: Record<string, unknown>;
}
