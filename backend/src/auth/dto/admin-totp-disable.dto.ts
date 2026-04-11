import { IsString, MinLength } from 'class-validator';

export class AdminTotpDisableDto {
  @IsString()
  @MinLength(1)
  password: string;
}
