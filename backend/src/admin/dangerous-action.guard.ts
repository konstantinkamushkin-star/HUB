import {
  BadRequestException,
  CanActivate,
  ExecutionContext,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { DANGEROUS_ACTION_KEY } from './dangerous-action.decorator';

@Injectable()
export class DangerousActionGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const actionName = this.reflector.getAllAndOverride<string>(
      DANGEROUS_ACTION_KEY,
      [context.getHandler(), context.getClass()],
    );

    if (!actionName) {
      return true;
    }

    const request = context.switchToHttp().getRequest<{
      headers?: Record<string, string>;
      body?: { reason?: string };
    }>();

    const confirmed = request.headers?.['x-admin-confirm-dangerous-action'];
    const reason = request.body?.reason?.trim();

    if (confirmed !== 'true') {
      throw new BadRequestException(
        `Dangerous action "${actionName}" requires x-admin-confirm-dangerous-action: true`,
      );
    }

    if (!reason || reason.length < 3) {
      throw new BadRequestException(
        `Dangerous action "${actionName}" requires non-empty reason`,
      );
    }

    return true;
  }
}
