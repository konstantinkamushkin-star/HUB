import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateMarineSpeciesDto {
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  scientificName: string;

  @IsString()
  @MinLength(1)
  @MaxLength(255)
  commonName: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  family?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  @MaxLength(2048)
  photoUrl?: string;

  @IsOptional()
  @IsString()
  @IsIn(['draft', 'published', 'hidden'])
  status?: string;
}

export class UpdateMarineSpeciesDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  scientificName?: string;

  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  commonName?: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  family?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsString()
  @MaxLength(2048)
  photoUrl?: string;

  @IsOptional()
  @IsString()
  @IsIn(['draft', 'published', 'hidden'])
  status?: string;
}
