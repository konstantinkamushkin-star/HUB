import { IsOptional, IsString, MaxLength } from 'class-validator';

export class RejectDiveSiteContributionDto {
  @IsOptional()
  @IsString()
  @MaxLength(4000)
  reason?: string;
}
