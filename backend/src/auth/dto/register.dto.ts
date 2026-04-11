import { ApiProperty } from '@nestjs/swagger';
import { IsEmail, IsString, MinLength, IsOptional, Matches, IsBoolean, Equals, MaxLength } from 'class-validator';

export class RegisterDto {
  @ApiProperty({ example: 'john.doe@example.com' })
  @IsEmail()
  email: string;

  @ApiProperty({ example: 'SecurePassword123!', minLength: 8 })
  @IsString()
  @MinLength(8)
  password: string;

  @ApiProperty({ example: 'John' })
  @IsString()
  firstName: string;

  @ApiProperty({ example: 'Doe' })
  @IsString()
  lastName: string;

  @ApiProperty({ example: '+1234567890', required: false })
  @IsOptional()
  @IsString()
  @Matches(/^\+?[1-9]\d{1,14}$/, { message: 'Invalid phone number format' })
  phone?: string;

  @ApiProperty({
    example: true,
    description: 'User consent to personal data processing',
  })
  @IsBoolean()
  @Equals(true, { message: 'Personal data processing consent is required' })
  personalDataConsent: boolean;

  @ApiProperty({
    example:
      'I consent to the processing of my personal data for account creation, authentication, communication, and service delivery in DiveHub.',
    description: 'Consent text shown to user at registration',
  })
  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  personalDataConsentText: string;
}
