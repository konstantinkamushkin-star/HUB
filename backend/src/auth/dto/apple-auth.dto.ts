import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBoolean,
  IsEmail,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
  Equals,
} from 'class-validator';

export class AppleAuthDto {
  @ApiProperty({ description: 'Apple identity token (JWT) from ASAuthorizationAppleIDCredential' })
  @IsString()
  @MinLength(10)
  idToken: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsEmail()
  email?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  firstName?: string;

  @ApiPropertyOptional()
  @IsOptional()
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
    description: 'Consent text shown in the app before Sign in with Apple',
    minLength: 20,
    maxLength: 2000,
  })
  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  personalDataConsentText: string;
}
