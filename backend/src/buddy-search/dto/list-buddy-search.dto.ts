import { IsOptional, IsString, MaxLength } from 'class-validator';

export class ListBuddySearchQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(200)
  place?: string;

  /** YYYY-MM-DD */
  @IsOptional()
  @IsString()
  @MaxLength(32)
  dateFrom?: string;

  /** YYYY-MM-DD */
  @IsOptional()
  @IsString()
  @MaxLength(32)
  dateTo?: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  certificationLevel?: string;

  /** Comma-separated language codes */
  @IsOptional()
  @IsString()
  @MaxLength(200)
  languages?: string;

  /** Comma-separated interest keys */
  @IsOptional()
  @IsString()
  @MaxLength(400)
  interests?: string;
}
