import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateComplianceRequestDto {
  @IsString()
  @IsIn(['pending', 'in_review', 'completed', 'rejected'])
  status: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  reason?: string;
}
