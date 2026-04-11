import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { CacheModule } from '@nestjs/cache-manager';
import { EventEmitterModule } from '@nestjs/event-emitter';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { AppController } from './app.controller';
import { LegalPagesController } from './legal-pages/legal-pages.controller';
import { DiveSitesModule } from './dive-sites/dive-sites.module';
import { DiveCentersModule } from './dive-centers/dive-centers.module';
import { AuthModule } from './auth/auth.module';
import { CoursesModule } from './courses/courses.module';
import { TripsModule } from './trips/trips.module';
import { ShopsModule } from './shops/shops.module';
import { UnderwaterAiModule } from './underwater-ai/underwater-ai.module';
import { ImageProcessingModule } from './image-processing/image-processing.module';
import { UvmProxyModule } from './uvm-proxy/uvm-proxy.module';
import { FriendsModule } from './friends/friends.module';
import { FeedModule } from './feed/feed.module';
import { ChatModule } from './chat/chat.module';
import { UsersModule } from './users/users.module';
import { MediaModule } from './media/media.module';
import { DiveLogsModule } from './dive-logs/dive-logs.module';
import { ReviewsModule } from './reviews/reviews.module';
import { AdminModule } from './admin/admin.module';
import { AnalyticsModule } from './analytics/analytics.module';
import { WebhooksModule } from './webhooks/webhooks.module';
import { PartnerModule } from './partner/partner.module';
import { TripImportModule } from './trip-import/trip-import.module';
import { NotificationsModule } from './notifications/notifications.module';

@Module({
  controllers: [AppController, LegalPagesController],
  imports: [
    // Configuration
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    ThrottlerModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        throttlers: [
          {
            name: 'default',
            ttl: Number(configService.get('THROTTLE_TTL_MS', 60000)),
            limit: Number(configService.get('THROTTLE_LIMIT', 120)),
          },
        ],
      }),
    }),
    EventEmitterModule.forRoot(),
    
    // Database
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres',
        host: configService.get('DB_HOST', 'localhost'),
        port: configService.get('DB_PORT', 5432),
        username: configService.get('DB_USERNAME', 'postgres'),
        password: configService.get('DB_PASSWORD', 'postgres'),
        database: configService.get('DB_DATABASE', 'divehub'),
        entities: [__dirname + '/**/*.entity{.ts,.js}'],
        synchronize: false, // Disable synchronize - use migrations instead
        logging: configService.get('NODE_ENV') === 'development',
        extra: {
          // PostGIS support
          max: 20,
          // iOS chat / i18n: avoid LATIN1 client sessions rejecting Cyrillic etc.
          client_encoding: 'UTF8',
        },
      }),
      inject: [ConfigService],
    }),
    
    // Redis Cache (with fallback to in-memory)
    CacheModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => {
        const redisHost = configService.get('REDIS_HOST', 'localhost');
        const redisPort = configService.get('REDIS_PORT', 6379);
        const redisPassword = configService.get('REDIS_PASSWORD');
        
        // Try to use Redis, fallback to in-memory if not available
        try {
          const { redisStore } = await import('cache-manager-redis-yet');
          return {
            store: await redisStore({
              socket: {
                host: redisHost,
                port: redisPort,
              },
              password: redisPassword || undefined,
            }),
            ttl: 300, // 5 minutes default
          };
        } catch (error) {
          console.warn(`⚠️ Redis not available at ${redisHost}:${redisPort}, using in-memory cache`);
          // Fallback to in-memory cache
          return {
            ttl: 300,
          };
        }
      },
      inject: [ConfigService],
      isGlobal: true,
    }),
    
    // Feature modules
    AuthModule,
    UsersModule,
    FriendsModule,
    FeedModule,
    ChatModule,
    MediaModule,
    DiveLogsModule,
    ReviewsModule,
    DiveSitesModule,
    DiveCentersModule,
    CoursesModule,
    TripsModule,
    ShopsModule,
    UnderwaterAiModule,
    ImageProcessingModule,
    UvmProxyModule,
    AdminModule,
    AnalyticsModule,
    WebhooksModule,
    PartnerModule,
    TripImportModule,
    NotificationsModule,
  ],
  providers: [
    {
      provide: APP_GUARD,
      useClass: ThrottlerGuard,
    },
  ],
})
export class AppModule {}
