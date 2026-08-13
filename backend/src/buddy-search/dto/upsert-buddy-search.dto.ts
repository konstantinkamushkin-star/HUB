import {
  ArrayMaxSize,
  IsArray,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

export class UpsertBuddySearchDto {
  @IsString()
  @MinLength(2)
  @MaxLength(200)
  place: string;

  /** YYYY-MM-DD */
  @IsString()
  @MaxLength(32)
  dateFrom: string;

  /** YYYY-MM-DD */
  @IsString()
  @MaxLength(32)
  dateTo: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  certificationLevel?: string;

  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100000)
  diveCount?: number;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(12)
  @IsString({ each: true })
  languages?: string[];

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(20)
  @IsString({ each: true })
  interests?: string[];
}
