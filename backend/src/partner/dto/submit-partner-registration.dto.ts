import { Type } from 'class-transformer';
import {
  IsEmail,
  IsIn,
  IsNumber,
  IsOptional,
  IsString,
  IsBoolean,
  Equals,
  Max,
  MaxLength,
  Min,
  MinLength,
  ValidateIf,
} from 'class-validator';

export class SubmitPartnerRegistrationDto {
  @IsIn(['dive_center', 'shop'])
  kind: 'dive_center' | 'shop';

  @IsString()
  @MinLength(2)
  @MaxLength(255)
  name: string;

  @IsOptional()
  @IsString()
  @MaxLength(4000)
  description?: string;

  @IsEmail()
  contactEmail: string;

  @IsString()
  @MinLength(5)
  @MaxLength(50)
  contactPhone: string;

  @IsString()
  @MinLength(2)
  @MaxLength(100)
  country: string;

  @IsString()
  @MinLength(2)
  @MaxLength(100)
  city: string;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  address?: string;

  @IsOptional()
  @IsString()
  @MaxLength(255)
  website?: string;

  @ValidateIf((o: SubmitPartnerRegistrationDto) => o.kind === 'shop')
  @IsOptional()
  @IsIn(['offline', 'online'])
  shopType?: 'offline' | 'online';

  @ValidateIf(
    (o: SubmitPartnerRegistrationDto) =>
      o.kind === 'dive_center' ||
      (o.kind === 'shop' &&
        (o.shopType === 'offline' || o.shopType === undefined)),
  )
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  latitude?: number;

  @ValidateIf(
    (o: SubmitPartnerRegistrationDto) =>
      o.kind === 'dive_center' ||
      (o.kind === 'shop' &&
        (o.shopType === 'offline' || o.shopType === undefined)),
  )
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  longitude?: number;

  @IsBoolean()
  @Equals(true, { message: 'Personal data processing consent is required' })
  personalDataConsent: boolean;

  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  personalDataConsentText: string;
}
