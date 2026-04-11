import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { AdminPortalAuthController } from './admin-portal-auth.controller';
import { AdminPortalAuthService } from './admin-portal-auth.service';
import { JwtStrategy } from './strategies/jwt.strategy';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { PermissionsGuard } from './rbac/permissions.guard';
import { MailModule } from '../mail/mail.module';

@Module({
  imports: [
    MailModule,
    TypeOrmModule.forFeature([User, DiveCenterEntity, ShopEntity]),
    PassportModule,
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => {
        const jwtSecret = configService.get<string>('JWT_SECRET');
        const isProduction = configService.get<string>('NODE_ENV') === 'production';

        if (!jwtSecret && isProduction) {
          throw new Error('JWT_SECRET is required in production');
        }

        return {
          secret: jwtSecret || 'dev-only-secret-key',
          // Время жизни задаём только в AuthService.generateTokens, чтобы не дублировать signOptions (иногда даёт 500 при логине).
        };
      },
      inject: [ConfigService],
    }),
  ],
  controllers: [AuthController, AdminPortalAuthController],
  providers: [AuthService, AdminPortalAuthService, JwtStrategy, PermissionsGuard],
  exports: [AuthService, JwtModule, PassportModule],
})
export class AuthModule {}
