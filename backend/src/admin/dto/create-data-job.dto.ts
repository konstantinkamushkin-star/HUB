import { IsIn, IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateDataJobDto {
  @IsString()
  @IsIn(['import', 'export'])
  type: string;

  @IsString()
  @IsIn(['csv', 'json', 'xlsx', 'pdf', 'backup'])
  format: string;

  @IsString()
  @MinLength(2)
  @MaxLength(64)
  targetType: string;

  @IsOptional()
  @IsObject()
  filters?: Record<string, unknown>;

  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}
