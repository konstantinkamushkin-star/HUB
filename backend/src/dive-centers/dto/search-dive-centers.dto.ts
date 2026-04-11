import { IsNumber, IsOptional, IsString, IsArray, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';

export class SearchDiveCentersDto {
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
  @Min(0)
  @Max(5)
  min_rating?: number;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  services?: string[];

  @IsOptional()
  @IsString()
  country?: string;

  @IsOptional()
  @IsString()
  sort?: string = 'distance'; // distance, rating, popularity

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

export class MapSearchCentersDto {
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
  @Min(0)
  @Max(5)
  min_rating?: number;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  services?: string[];

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(500)
  limit?: number = 500;
}

export class PopularDiveCentersDto {
  @IsOptional()
  @IsString()
  country?: string;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(100)
  limit?: number = 20;
}
