import { IsIn, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateVerificationRequestDto {
  @IsString()
  @IsIn(['pending', 'verified', 'rejected', 'more_info', 'revoked'])
  status: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  decisionNote?: string;

  /** Для DangerousActionGuard (PATCH верификации): обязательно при подтверждении в заголовке. */
  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(1000)
  reason?: string;
}
