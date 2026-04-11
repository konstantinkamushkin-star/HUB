import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateComplianceRequestDto {
  @IsString()
  userId: string;

  @IsString()
  @IsIn(['export_data', 'delete_data'])
  type: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  reason?: string;
}
