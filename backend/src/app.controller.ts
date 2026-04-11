import {
  Controller,
  Get,
  HttpStatus,
  HttpException,
  Inject,
} from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import { Cache } from 'cache-manager';

@Controller()
export class AppController {
  constructor(
    @InjectDataSource() private dataSource: DataSource,
    @Inject(CACHE_MANAGER) private cacheManager: Cache,
  ) {}

  @Get()
  getHello() {
    return {
      message: 'DiveHub backend API',
      note: 'Это только REST API. Админ-панель — отдельное приложение admin-web (обычно порт 3001).',
      try: {
        apiInfo: 'GET /api',
        health: 'GET /api/health',
        privacyHtml: 'GET /privacy',
        agreementHtml: 'GET /agreement',
        authLogin: 'POST /api/auth/login',
        adminExample: 'GET /api/admin/dashboard/overview (Bearer JWT)',
      },
    };
  }

  @SkipThrottle()
  @Get('health')
  async health() {
    const health = {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      db: 'unknown',
      cache: 'unknown',
    };

    // Check database
    try {
      await this.dataSource.query('SELECT 1');
      health.db = 'up';
    } catch (error) {
      health.db = 'down';
      health.status = 'unhealthy';
    }

    // Check cache (Redis or in-memory)
    try {
      await this.cacheManager.get('health-check');
      health.cache = 'up';
    } catch (error) {
      // Cache might be down but that's not critical
      health.cache = 'down';
    }

    if (health.status === 'unhealthy') {
      throw new HttpException(health, HttpStatus.SERVICE_UNAVAILABLE);
    }

    return health;
  }
}
