import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class ModerationActionDto {
  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason?: string;
}
