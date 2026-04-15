import { IsIn, IsNumber, IsOptional, IsString, MaxLength, Min } from 'class-validator';

export class UpdateBookingStatusDto {
  @IsIn(['pending', 'quoted', 'confirmed', 'completed', 'cancelled', 'refunded'])
  status:
    | 'pending'
    | 'quoted'
    | 'confirmed'
    | 'completed'
    | 'cancelled'
    | 'refunded';

  @IsOptional()
  @IsNumber()
  @Min(0)
  finalPriceAmount?: number;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  finalPriceCurrency?: string;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  manualVerificationNote?: string;
}
