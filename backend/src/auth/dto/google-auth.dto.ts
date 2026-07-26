import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsBoolean,
  IsEmail,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
  Equals,
} from 'class-validator';

/** `@IsOptional` skips only null/undefined — empty string still fails `@IsEmail`. */
function emptyToUndefined({ value }: { value: unknown }): unknown {
  if (typeof value === 'string' && value.trim() === '') {
    return undefined;
  }
  return typeof value === 'string' ? value.trim() : value;
}

export class GoogleAuthDto {
  @ApiProperty({ description: 'Google ID token from GIDGoogleUser.idToken' })
  @IsString()
  @MinLength(10)
  idToken: string;

  @ApiPropertyOptional({ description: 'Optional; server verifies using id_token' })
  @IsOptional()
  @IsString()
  accessToken?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsEmail()
  email?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  firstName?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @Transform(emptyToUndefined)
  @IsString()
  lastName?: string;

  @ApiProperty({
    example: true,
    description: 'User consent to personal data processing (required)',
  })
  @IsBoolean()
  @Equals(true, { message: 'Personal data processing consent is required' })
  personalDataConsent: boolean;

  @ApiProperty({
    description: 'Consent text shown in the app before Google sign-in',
    minLength: 20,
    maxLength: 2000,
  })
  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  personalDataConsentText: string;
}
