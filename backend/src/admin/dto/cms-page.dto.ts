import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateCmsPageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  slug: string;

  @IsOptional()
  @IsString()
  @MaxLength(16)
  locale?: string;

  @IsString()
  @MinLength(1)
  @MaxLength(512)
  title: string;

  @IsOptional()
  @IsString()
  body?: string;

  @IsOptional()
  @IsString()
  @IsIn(['draft', 'published', 'archived'])
  status?: string;
}

export class UpdateCmsPageDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  slug?: string;

  @IsOptional()
  @IsString()
  @MaxLength(16)
  locale?: string;

  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(512)
  title?: string;

  @IsOptional()
  @IsString()
  body?: string;

  @IsOptional()
  @IsString()
  @IsIn(['draft', 'published', 'archived'])
  status?: string;
}
