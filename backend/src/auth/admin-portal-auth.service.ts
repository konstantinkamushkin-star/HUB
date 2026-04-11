import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { generateSecret, generateURI, verifySync } from 'otplib';
import { User } from '../users/entities/user.entity';
import { AuthService, serializePublicUser } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { isAdminRole } from './rbac/admin-roles';

const PRE_AUTH_PURPOSE = 'admin_pre_2fa';

@Injectable()
export class AdminPortalAuthService {
  constructor(
    private readonly authService: AuthService,
    private readonly jwtService: JwtService,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
  ) {}

  async adminLogin(dto: LoginDto) {
    const user = await this.authService.validateCredentials(dto.email, dto.password);
    if (!isAdminRole(user.role)) {
      throw new ForbiddenException('Admin access only');
    }

    const needs2fa = Boolean(user.adminTotpEnabled && user.adminTotpSecret);
    if (needs2fa) {
      const preAuthToken = await this.jwtService.signAsync(
        { sub: user.id, purpose: PRE_AUTH_PURPOSE },
        { expiresIn: '5m' },
      );
      return {
        requiresTwoFactor: true as const,
        preAuthToken,
        user: serializePublicUser(user),
      };
    }

    await this.authService.touchLastLogin(user.id);
    const tokens = await this.authService.issueTokenPair(user.id);
    return {
      requiresTwoFactor: false as const,
      ...tokens,
      user: serializePublicUser(user),
    };
  }

  async verifyAdmin2fa(preAuthToken: string, code: string) {
    let payload: { sub: string; purpose?: string };
    try {
      payload = this.jwtService.verify(preAuthToken) as { sub: string; purpose?: string };
    } catch {
      throw new UnauthorizedException('Invalid or expired pre-auth token');
    }
    if (payload.purpose !== PRE_AUTH_PURPOSE) {
      throw new UnauthorizedException('Invalid token purpose');
    }

    const user = await this.usersRepo.findOne({ where: { id: payload.sub } });
    if (!user || !isAdminRole(user.role)) {
      throw new UnauthorizedException('User not found');
    }
    if (!user.adminTotpSecret || !user.adminTotpEnabled) {
      throw new BadRequestException('Two-factor authentication is not enabled');
    }

    const totpOk = verifySync({ secret: user.adminTotpSecret, token: code });
    if (!totpOk.valid) {
      throw new UnauthorizedException('Invalid verification code');
    }

    await this.authService.touchLastLogin(user.id);
    const tokens = await this.authService.issueTokenPair(user.id);
    return {
      ...tokens,
      user: serializePublicUser(user),
    };
  }

  async beginTotpSetup(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user || !isAdminRole(user.role)) {
      throw new ForbiddenException('Admin access only');
    }
    if (user.adminTotpEnabled) {
      throw new BadRequestException('Two-factor authentication is already enabled');
    }

    let secret = user.adminTotpSecret;
    if (!secret) {
      secret = generateSecret();
      user.adminTotpSecret = secret;
      await this.usersRepo.save(user);
    }

    const otpauthUrl = generateURI({
      issuer: 'DiveHub Admin',
      label: user.email,
      secret,
    });
    return { secret, otpauthUrl };
  }

  async confirmTotpSetup(userId: string, code: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user?.adminTotpSecret) {
      throw new BadRequestException('Run TOTP setup first');
    }
    if (user.adminTotpEnabled) {
      return { enabled: true };
    }

    const totpOk = verifySync({ secret: user.adminTotpSecret, token: code });
    if (!totpOk.valid) {
      throw new UnauthorizedException('Invalid verification code');
    }
    user.adminTotpEnabled = true;
    await this.usersRepo.save(user);
    return { enabled: true };
  }

  async disableTotp(userId: string, password: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (!user.password) {
      throw new BadRequestException('Password not set');
    }
    const valid = await bcrypt.compare(password, user.password);
    if (!valid) {
      throw new UnauthorizedException('Invalid password');
    }
    user.adminTotpSecret = null;
    user.adminTotpEnabled = false;
    await this.usersRepo.save(user);
    return { disabled: true };
  }
}
