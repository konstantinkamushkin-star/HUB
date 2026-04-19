import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { AdminRole } from '../auth/rbac/admin-roles';

@Injectable()
export class AdminOrSuperAdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{
      user?: { role?: string };
    }>();
    const role = request.user?.role;
    if (role !== AdminRole.SUPER_ADMIN && role !== AdminRole.ADMIN) {
      throw new ForbiddenException('Admin or super admin access required');
    }
    return true;
  }
}
