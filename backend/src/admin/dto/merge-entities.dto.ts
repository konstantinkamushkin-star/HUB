import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class MergeEntitiesDto {
  @IsString()
  @MinLength(2)
  @MaxLength(128)
  sourceId: string;

  @IsString()
  @MinLength(2)
  @MaxLength(128)
  targetId: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  reason?: string;
}
