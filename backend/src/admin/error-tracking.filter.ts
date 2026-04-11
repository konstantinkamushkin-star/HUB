import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { ErrorStatsService } from './error-stats.service';

@Catch()
export class ErrorTrackingFilter implements ExceptionFilter {
  constructor(private readonly errorStatsService: ErrorStatsService) {}

  catch(exception: unknown, host: ArgumentsHost) {
    if (host.getType() !== 'http') {
      return;
    }

    const response = host.switchToHttp().getResponse();

    const statusCode =
      exception instanceof HttpException
        ? exception.getStatus()
        : HttpStatus.INTERNAL_SERVER_ERROR;

    const message =
      exception instanceof HttpException
        ? String(exception.message ?? 'Http exception')
        : String((exception as Error)?.message ?? 'Unknown exception');

    this.errorStatsService.reportHttpError(statusCode, message);

    if (response?.headersSent) {
      return;
    }

    if (exception instanceof HttpException) {
      const payload = exception.getResponse();
      response.status(statusCode).json(payload);
      return;
    }

    const exposeDetails =
      process.env.NODE_ENV !== 'production' ||
      process.env.EXPOSE_ERROR_DETAILS === 'true';
    const err = exception instanceof Error ? exception : null;

    response.status(statusCode).json({
      statusCode,
      message:
        exposeDetails && err?.message?.length
          ? err.message
          : 'Internal server error',
      ...(exposeDetails && err?.name ? { error: err.name } : {}),
    });
  }
}
