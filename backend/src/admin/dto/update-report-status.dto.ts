import { IsEnum, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';
import { ReportPriority, ReportStatus } from '../../common/statuses';

export class UpdateReportStatusDto {
  @IsEnum(ReportStatus)
  status: ReportStatus;

  @IsOptional()
  @IsEnum(ReportPriority)
  priority?: ReportPriority;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  resolution?: string;
}
