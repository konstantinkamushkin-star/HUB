import { IsEnum, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';
import { AdminRole } from '../../auth/rbac/admin-roles';

export class UpdateUserRoleDto {
  @IsEnum(AdminRole)
  role: AdminRole;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason?: string;
}
