import { IsString, MinLength, MaxLength } from 'class-validator';

export class Admin2faVerifyDto {
  @IsString()
  @MinLength(10)
  preAuthToken: string;

  @IsString()
  @MinLength(6)
  @MaxLength(10)
  code: string;
}
