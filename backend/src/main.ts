import { NestFactory } from '@nestjs/core';
import { RequestMethod, ValidationPipe } from '@nestjs/common';
import { WsAdapter } from '@nestjs/platform-ws';
import helmet from 'helmet';
import type { Express } from 'express';
import { AppModule } from './app.module';
import { PerformanceInterceptor } from './common/interceptors/performance.interceptor';
import { ErrorStatsService } from './admin/error-stats.service';

function envBool(name: string, fallback: boolean): boolean {
  const raw = process.env[name]?.trim().toLowerCase();
  if (!raw) {
    return fallback;
  }
  return ['1', 'true', 'yes', 'on'].includes(raw);
}

function applyTrustProxy(expressApp: Express) {
  const raw = process.env.TRUST_PROXY?.trim();
  if (!raw) {
    return;
  }
  if (raw === 'true') {
    expressApp.set('trust proxy', true);
    return;
  }
  if (/^\d+$/.test(raw)) {
    expressApp.set('trust proxy', parseInt(raw, 10));
    return;
  }
  expressApp.set('trust proxy', raw);
}

async function bootstrap() {
  try {
    const app = await NestFactory.create(AppModule);
    app.useWebSocketAdapter(new WsAdapter(app));
    app.enableShutdownHooks();

    const expressApp = app.getHttpAdapter().getInstance() as Express;
    applyTrustProxy(expressApp);
    expressApp.disable('x-powered-by');
    app.use(
      helmet({
        contentSecurityPolicy: false,
        crossOriginResourcePolicy: { policy: 'cross-origin' },
      }),
    );

    const isProduction = process.env.NODE_ENV === 'production';
    const corsOriginsRaw = process.env.CORS_ORIGINS?.trim();
    const corsOrigins = corsOriginsRaw
      ? corsOriginsRaw.split(',').map((origin) => origin.trim()).filter(Boolean)
      : [];
    const corsOriginConfig = corsOrigins.length > 0 ? corsOrigins : isProduction ? false : true;

    if (isProduction && corsOriginConfig === false) {
      throw new Error('CORS_ORIGINS is required in production');
    }

    // Enable CORS
    app.enableCors({
      origin: corsOriginConfig,
      credentials: true,
      methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
      allowedHeaders: [
        'Content-Type',
        'Content-Length',
        'Accept-Encoding',
        'X-CSRF-Token',
        'Authorization',
        'Accept',
        'Origin',
        'Cache-Control',
        'X-Requested-With',
        'X-Admin-Confirm-Dangerous-Action',
        'X-Correlation-Id',
      ],
    });
    
    // Global validation pipe
    const forbidNonWhitelisted = envBool(
      'FORBID_NON_WHITELISTED',
      isProduction,
    );
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        transform: true,
        forbidNonWhitelisted,
        forbidUnknownValues: true,
      }),
    );
    
    // Global performance interceptor
    app.useGlobalInterceptors(new PerformanceInterceptor());
    
    // Global prefix (GET / stays at root so browsers opening localhost:PORT see a helpful JSON)
    app.setGlobalPrefix('api', {
      exclude: [
        { path: '/', method: RequestMethod.GET },
        { path: 'privacy', method: RequestMethod.GET },
        { path: 'agreement', method: RequestMethod.GET },
      ],
    });

    const errorStatsService = app.get(ErrorStatsService);
    
    const port = process.env.PORT || 3000;
    
    // Add error handlers to prevent server crashes
    process.on('uncaughtException', (error) => {
      console.error('Uncaught Exception:', error);
      errorStatsService.reportUncaughtException(
        error instanceof Error ? error.message : String(error),
      );
    });
    
    process.on('unhandledRejection', (reason, promise) => {
      console.error('Unhandled Rejection at:', promise, 'reason:', reason);
      errorStatsService.reportUnhandledRejection(
        reason instanceof Error ? reason.message : String(reason),
      );
    });
    
    await app.listen(port, '0.0.0.0');
    
    console.log(`🚀 DiveHub Backend is running on: http://localhost:${port}`);
    console.log(`📋 Root hint JSON: http://localhost:${port}/  (API lives under /api/...)`);
    console.log(`📍 Geo API endpoints available at: http://localhost:${port}/api/v1/dive-sites`);
    console.log(`🔐 Auth endpoints available at: http://localhost:${port}/api/auth`);
    console.log(`🖼️ Underwater AI: POST http://localhost:${port}/api/v1/underwater-ai/process (set AI_UNDERWATER_SERVICE_URL to enable)`);
    console.log(`🖼️ UVM proxy (photo/video + SeaSplat): POST http://localhost:${port}/api/v1/process/photo/{engine}, /api/v1/process/video/{engine}, /api/v1/seasplat/* → UVM_URL (default http://127.0.0.1:8010)`);
    console.log(`🖼️ Image jobs: POST http://localhost:${port}/api/v1/image/upload → /api/v1/image/process → status/result`);
    console.log(`💬 Chat WebSocket: ws://localhost:${port}/ws/chat?token=<jwt>`);
    console.log(`📷 Media upload: POST http://localhost:${port}/api/media/upload (auth)`);
  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

bootstrap();
