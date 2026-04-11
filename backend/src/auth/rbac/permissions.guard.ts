import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { isAdminRole } from './admin-roles';
import { REQUIRED_PERMISSIONS_KEY } from './permissions.decorator';
import { Permission, ROLE_PERMISSIONS } from './permissions';

@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredPermissions =
      this.reflector.getAllAndOverride<Permission[]>(REQUIRED_PERMISSIONS_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) ?? [];

    if (!requiredPermissions.length) {
      return true;
    }

    const request = context.switchToHttp().getRequest<{
      user?: { role?: string };
    }>();
    const role = request.user?.role;

    if (!isAdminRole(role)) {
      throw new ForbiddenException('Admin role required');
    }

    const rolePermissions = ROLE_PERMISSIONS[role] ?? [];
    const hasAllPermissions = requiredPermissions.every((permission) =>
      rolePermissions.includes(permission),
    );

    if (!hasAllPermissions) {
      throw new ForbiddenException('Insufficient permissions');
    }

    return true;
  }
}
