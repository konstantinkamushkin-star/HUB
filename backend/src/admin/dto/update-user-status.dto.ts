import { IsEnum, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';
import { UserAccountStatus } from '../../common/statuses';

export class UpdateUserStatusDto {
  @IsEnum(UserAccountStatus)
  status: UserAccountStatus;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason?: string;
}
