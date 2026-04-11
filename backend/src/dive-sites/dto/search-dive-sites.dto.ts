import { IsNumber, IsOptional, IsString, IsArray, Min, Max } from 'class-validator';
import { Type, Transform } from 'class-transformer';

export class SearchDiveSitesDto {
  @IsNumber()
  @Type(() => Number)
  lat: number;

  @IsNumber()
  @Type(() => Number)
  lng: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1000)
  @Max(1000000)
  radius?: number = 50000; // meters, default 50km

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(4)
  difficulty?: number;

  @IsOptional()
  // site_types accepts both string and string[] - transformed in controller
  site_types?: any;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  min_depth?: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  max_depth?: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(0)
  @Max(5)
  min_rating?: number;

  @IsOptional()
  @Transform(({ value }) => {
    if (!value) return undefined;
    // If it's already an array, return as is
    if (Array.isArray(value)) return value;
    // If it's a string, convert to array with one element
    if (typeof value === 'string') return [value];
    return value;
  })
  @IsArray()
  @IsString({ each: true })
  access_type?: string[];

  @IsOptional()
  @IsString()
  country?: string;

  @IsOptional()
  @IsString()
  sort?: string = 'distance'; // distance, rating, popularity, newest

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(100)
  limit?: number = 20;

  @IsOptional()
  @IsString()
  cursor?: string;
}

export class MapSearchDto {
  @IsNumber()
  @Type(() => Number)
  north: number;

  @IsNumber()
  @Type(() => Number)
  south: number;

  @IsNumber()
  @Type(() => Number)
  east: number;

  @IsNumber()
  @Type(() => Number)
  west: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(4)
  difficulty?: number;

  @IsOptional()
  @Transform(({ value }) => {
    if (value === undefined || value === null) return undefined;
    // If it's already an array, return as is
    if (Array.isArray(value)) return value;
    // If it's a string, convert to array with one element
    if (typeof value === 'string') return [value];
    return value;
  })
  @Type(() => String)
  @IsArray()
  @IsString({ each: true })
  site_types?: string[];

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(0)
  @Max(5)
  min_rating?: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(500)
  limit?: number = 500;
}

export class PopularDiveSitesDto {
  @IsOptional()
  @IsString()
  country?: string;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(500)
  limit?: number = 20;

  /** For legacy list pagination (paired with `limit`). */
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(0)
  @Max(500_000)
  offset?: number = 0;
}

export class ClustersDto {
  @IsNumber()
  @Type(() => Number)
  north: number;

  @IsNumber()
  @Type(() => Number)
  south: number;

  @IsNumber()
  @Type(() => Number)
  east: number;

  @IsNumber()
  @Type(() => Number)
  west: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(20)
  zoom?: number = 10;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(4)
  difficulty?: number;

  @IsOptional()
  @IsString()
  site_types?: string;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(0)
  @Max(5)
  min_rating?: number;
}
