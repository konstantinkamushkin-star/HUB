import {
  IsArray,
  IsBoolean,
  IsDateString,
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  Min,
} from 'class-validator';

export class CreateDiveLogDto {
  @IsOptional()
  @IsUUID()
  diveSiteId?: string;

  @IsDateString()
  date: string;

  @IsOptional()
  @IsDateString()
  startTime?: string;

  @IsOptional()
  @IsDateString()
  endTime?: string;

  @IsInt()
  @Min(1)
  duration: number;

  @IsNumber()
  @Min(0.01)
  maxDepth: number;

  @IsOptional()
  @IsNumber()
  averageDepth?: number;

  @IsOptional()
  @IsNumber()
  waterTemperature?: number;

  @IsOptional()
  @IsNumber()
  visibility?: number;

  @IsOptional()
  @IsString()
  current?: string;

  @IsOptional()
  @IsString()
  diveType?: string;

  @IsOptional()
  @IsString()
  notes?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  photoUrls?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  videoUrls?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  fishSpecies?: string[];

  @IsOptional()
  @IsBoolean()
  isPublished?: boolean;
}
