import { IsIn, IsOptional, IsString, MaxLength } from 'class-validator';
import { Transform } from 'class-transformer';
import type { CertificationVerificationStatus } from '../entities/user-certification.entity';

export class CreateCertificationDto {
  @IsString()
  @MaxLength(128)
  @Transform(({ obj }) => {
    const raw = obj?.agency ?? obj?.organization;
    return typeof raw === 'string' ? raw.trim() : raw;
  })
  agency: string;

  @IsString()
  @MaxLength(256)
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  level: string;

  @IsString()
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  issueDate: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  instructorNumber?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  certificateNumber?: string | null;

  @IsOptional()
  @IsString()
  cardImageUrl?: string | null;

  @IsOptional()
  @IsIn(['PENDING', 'VERIFIED', 'REJECTED'])
  verificationStatus?: CertificationVerificationStatus;
}
