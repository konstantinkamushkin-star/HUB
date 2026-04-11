import { Injectable } from '@nestjs/common';

type ErrorPreview = {
  timestamp: string;
  kind: 'http' | 'uncaughtException' | 'unhandledRejection';
  message: string;
  statusCode?: number;
};

@Injectable()
export class ErrorStatsService {
  private httpErrorsTotal = 0;
  private uncaughtExceptionsTotal = 0;
  private unhandledRejectionsTotal = 0;
  private httpByStatus: Record<string, number> = {};
  private recent: ErrorPreview[] = [];

  reportHttpError(statusCode: number, message: string) {
    this.httpErrorsTotal += 1;
    const key = String(statusCode);
    this.httpByStatus[key] = (this.httpByStatus[key] ?? 0) + 1;
    this.pushRecent({
      timestamp: new Date().toISOString(),
      kind: 'http',
      message,
      statusCode,
    });
  }

  reportUncaughtException(message: string) {
    this.uncaughtExceptionsTotal += 1;
    this.pushRecent({
      timestamp: new Date().toISOString(),
      kind: 'uncaughtException',
      message,
    });
  }

  reportUnhandledRejection(message: string) {
    this.unhandledRejectionsTotal += 1;
    this.pushRecent({
      timestamp: new Date().toISOString(),
      kind: 'unhandledRejection',
      message,
    });
  }

  getStats() {
    return {
      totals: {
        httpErrors: this.httpErrorsTotal,
        uncaughtExceptions: this.uncaughtExceptionsTotal,
        unhandledRejections: this.unhandledRejectionsTotal,
        allErrors:
          this.httpErrorsTotal +
          this.uncaughtExceptionsTotal +
          this.unhandledRejectionsTotal,
      },
      httpByStatus: this.httpByStatus,
      process: {
        uptimeSeconds: Math.floor(process.uptime()),
        memory: process.memoryUsage(),
      },
      recent: this.recent,
    };
  }

  private pushRecent(event: ErrorPreview) {
    this.recent.unshift(event);
    if (this.recent.length > 50) {
      this.recent = this.recent.slice(0, 50);
    }
  }
}
