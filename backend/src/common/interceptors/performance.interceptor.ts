import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
} from '@nestjs/common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable()
export class PerformanceInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<any> {
    const response = context.switchToHttp().getResponse();
    const startTime = Date.now();

    return next.handle().pipe(
      map((data) => {
        const duration = Date.now() - startTime;
        try {
          if (!response.headersSent) {
            response.setHeader('X-Response-Time', `${duration}ms`);
          }
        } catch {
          // Avoid crashing the request if headers were already committed.
        }
        return data;
      }),
    );
  }
}
