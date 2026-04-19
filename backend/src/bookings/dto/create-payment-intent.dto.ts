import { IsNumber, IsString, IsUUID, MaxLength, Min } from 'class-validator';

export class CreatePaymentIntentDto {
  @IsUUID()
  diveCenterId: string;

  @IsNumber()
  @Min(0)
  amount: number;

  @IsString()
  @MaxLength(8)
  currency: string;
}
