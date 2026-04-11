import { IsString, MinLength, MaxLength } from 'class-validator';

export class AdminTotpConfirmDto {
  @IsString()
  @MinLength(6)
  @MaxLength(10)
  code: string;
}
