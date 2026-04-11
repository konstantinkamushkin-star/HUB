import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';

@Injectable()
export class SuperAdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{
      user?: { role?: string };
    }>();
    const role = request.user?.role;
    if (role !== 'SUPER_ADMIN') {
      throw new ForbiddenException('Super admin access required');
    }
    return true;
  }
}
