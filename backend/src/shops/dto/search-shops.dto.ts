import { IsOptional, IsNumber, IsString, IsEnum, IsBoolean, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';

export class SearchShopsDto {
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
  radius?: number = 50000; // Default 50km

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(100)
  limit?: number = 20;

  @IsOptional()
  @IsString()
  cursor?: string;

  @IsOptional()
  @IsEnum(['offline', 'online'])
  type?: string;

  @IsOptional()
  @IsBoolean()
  @Type(() => Boolean)
  serviceAvailable?: boolean;

  @IsOptional()
  @IsString()
  search?: string;
}

/** Public list when client has no lat/lng (matches dive-centers/popular). */
export class PopularShopsDto {
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(100)
  limit?: number = 20;
}

export class MapSearchShopsDto {
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
  @IsEnum(['offline', 'online'])
  type?: string;
}
