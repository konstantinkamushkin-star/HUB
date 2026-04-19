import {
  IsIn,
  IsNumber,
  IsOptional,
  IsString,
  MaxLength,
  Min,
} from 'class-validator';

/** Mirrors iOS `Booking.Payment` / Android `BookingPaymentDto`. */
export class PaymentDto {
  @IsString()
  @IsIn(['online', 'on_site', 'apple_pay', 'google_pay'])
  method: string;

  @IsNumber()
  @Min(0)
  amount: number;

  @IsString()
  @MaxLength(8)
  currency: string;

  @IsString()
  @IsIn(['pending', 'paid', 'refunded', 'failed'])
  status: string;

  @IsOptional()
  @IsString()
  @MaxLength(256)
  transactionId?: string | null;

  @IsOptional()
  @IsString()
  paidAt?: string | null;
}
