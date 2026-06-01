import { IsOptional, IsString, MaxLength } from 'class-validator';
import { Transform } from 'class-transformer';

export class UpdateCertificationDto {
  @IsOptional()
  @IsString()
  @MaxLength(128)
  @Transform(({ obj }) => {
    const raw = obj?.agency ?? obj?.organization;
    return typeof raw === 'string' ? raw.trim() : raw;
  })
  agency?: string;

  @IsOptional()
  @IsString()
  @MaxLength(256)
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  level?: string;

  @IsOptional()
  @IsString()
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  issueDate?: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  instructorNumber?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  certificateNumber?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(2048)
  cardImageUrl?: string | null;
}
