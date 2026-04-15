import {
  Injectable,
  UnauthorizedException,
  BadRequestException,
  NotFoundException,
  ConflictException,
  ServiceUnavailableException,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { randomBytes } from 'crypto';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { ForgotPasswordDto } from './dto/forgot-password.dto';
import { VerifyResetCodeDto } from './dto/verify-reset-code.dto';
import { ResetPasswordDto } from './dto/reset-password.dto';
import { MailService } from '../mail/mail.service';
import { AppleAuthDto } from './dto/apple-auth.dto';
import { GoogleAuthDto } from './dto/google-auth.dto';
import {
  verifyAppleIdentityToken,
  verifyGoogleIdentityToken,
} from './oauth-id-token.util';
import { UserAccountStatus } from '../common/statuses';

/** Убирает секретные поля перед отдачей в JSON. */
export function serializePublicUser(user: User): Omit<User, 'password' | 'adminTotpSecret'> {
  const { password: _p, adminTotpSecret: _t, ...rest } = user;
  return rest;
}

/** Профиль для клиента: id дайв-центра / магазина (в таблице users этих полей нет). */
export type PublicUserWithPartners = Omit<User, 'password' | 'adminTotpSecret'> & {
  diveCenterId: string | null;
  shopId: string | null;
};

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** Deep-merge nested plain objects; overwrites arrays and scalars. */
function mergeDiverProfile(
  current: Record<string, unknown> | null | undefined,
  patch: Record<string, unknown>,
): Record<string, unknown> {
  const base: Record<string, unknown> =
    current && isPlainObject(current) ? { ...current } : {};
  for (const key of Object.keys(patch)) {
    const pv = patch[key];
    const bv = base[key];
    if (isPlainObject(pv) && isPlainObject(bv)) {
      base[key] = mergeDiverProfile(bv, pv);
    } else {
      base[key] = pv;
    }
  }
  return base;
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    @InjectRepository(User)
    private userRepository: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private diveCentersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private shopsRepo: Repository<ShopEntity>,
    private jwtService: JwtService,
    private readonly mailService: MailService,
    private readonly configService: ConfigService,
  ) {}

  /**
   * Раньше только owner_id; у части аккаунтов DIVE_CENTER_ADMIN центр есть, но owner_id не выставлен
   * (ручные данные, старые сиды). Тогда ищем по instructor_ids и по email контакта центра.
   */
  /** Для проверки прав на курсы центра и др. (в `users` поля dive_center_id нет). */
  async getDiveCenterIdForUser(userId: string): Promise<string | null> {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      return null;
    }
    return this.resolveDiveCenterIdForUser(user);
  }

  private async resolveDiveCenterIdForUser(user: User): Promise<string | null> {
    const byOwner = await this.diveCentersRepo.findOne({
      where: { owner_id: user.id },
      select: ['id'],
    });
    if (byOwner) {
      return byOwner.id;
    }

    const byInstructorSlot = await this.diveCentersRepo
      .createQueryBuilder('dc')
      .select(['dc.id'])
      .where(':uid = ANY(dc.instructor_ids)', { uid: user.id })
      .getOne();
    if (byInstructorSlot) {
      return byInstructorSlot.id;
    }

    const role = (user.role ?? '').toUpperCase();
    if (role === 'DIVE_CENTER_ADMIN' && user.email?.trim()) {
      const em = user.email.toLowerCase().trim();
      const byContactEmail = await this.diveCentersRepo
        .createQueryBuilder('dc')
        .select(['dc.id'])
        .where('LOWER(TRIM(dc.email)) = :em', { em })
        .getOne();
      if (byContactEmail) {
        return byContactEmail.id;
      }
    }

    return null;
  }

  private async toPublicUserWithPartners(user: User): Promise<PublicUserWithPartners> {
    const base = serializePublicUser(user);
    const [diveCenterId, shop] = await Promise.all([
      this.resolveDiveCenterIdForUser(user),
      this.shopsRepo.findOne({
        where: { owner_id: user.id },
        select: ['id'],
      }),
    ]);
    return {
      ...base,
      diveCenterId,
      shopId: shop?.id ?? null,
    };
  }

  /**
   * В средах с неполными миграциями таблицы/колонки партнёров могут отсутствовать.
   * В таком случае не роняем auth-флоу (register/login/me), а возвращаем базовый профиль.
   */
  private async toPublicUserWithPartnersSafe(user: User): Promise<PublicUserWithPartners> {
    try {
      return await this.toPublicUserWithPartners(user);
    } catch (error) {
      this.logger.warn(
        `Partner enrichment failed for user ${user.id}: ${error instanceof Error ? error.message : String(error)}`,
      );
      return {
        ...serializePublicUser(user),
        diveCenterId: null,
        shopId: null,
      };
    }
  }

  async register(registerDto: RegisterDto) {
    const {
      email,
      password,
      firstName,
      lastName,
      phone,
      personalDataConsent,
      personalDataConsentText,
    } = registerDto;

    if (!personalDataConsent) {
      throw new BadRequestException('Personal data processing consent is required');
    }

    // Normalize email to lowercase for consistency
    const emailNormalized = email?.toLowerCase().trim();

    // Check if user already exists (case-insensitive)
    const existingUser = await this.userRepository
      .createQueryBuilder('user')
      .where('LOWER(user.email) = LOWER(:email)', { email })
      .getOne();

    if (existingUser) {
      throw new BadRequestException('User with this email already exists');
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // Create user with normalized email
    const user = this.userRepository.create({
      email: emailNormalized,
      password: hashedPassword,
      firstName,
      lastName,
      phone,
      role: 'DIVER_BASIC',
    });

    const savedUser = await this.userRepository.save(user);

    this.logger.log(
      `Personal data consent accepted for ${emailNormalized} at ${new Date().toISOString()}; text length=${personalDataConsentText.trim().length}`,
    );

    const tokens = await this.generateTokens(savedUser.id);

    return {
      ...tokens,
      user: await this.toPublicUserWithPartnersSafe(savedUser),
    };
  }

  /** Проверка email+password; кидает Unauthorized при ошибке. */
  async validateCredentials(email: string, password: string): Promise<User> {
    const emailNormalized = email?.toLowerCase().trim();
    let user = await this.userRepository.findOne({
      where: { email: emailNormalized },
    });
    if (!user) {
      user = await this.userRepository
        .createQueryBuilder('user')
        .where('LOWER(user.email) = LOWER(:email)', { email })
        .getOne();
    }
    if (!user) {
      throw new UnauthorizedException('Invalid email or password');
    }
    if (user.accountStatus === UserAccountStatus.DELETED || user.deletedAt) {
      throw new UnauthorizedException('Account is deleted');
    }
    if (!user.password) {
      this.logger.warn(`User ${user.id} has empty password hash`);
      throw new UnauthorizedException('Invalid email or password');
    }
    let isPasswordValid = false;
    try {
      isPasswordValid = await bcrypt.compare(password, user.password);
    } catch (e) {
      this.logger.warn(
        `bcrypt.compare failed for user ${user.id}: ${e instanceof Error ? e.message : String(e)}`,
      );
      throw new UnauthorizedException('Invalid email or password');
    }
    if (!isPasswordValid) {
      throw new UnauthorizedException('Invalid email or password');
    }
    return user;
  }

  async touchLastLogin(userId: string): Promise<void> {
    try {
      await this.userRepository
        .createQueryBuilder()
        .update(User)
        .set({ lastLogin: new Date() })
        .where('id = :id', { id: userId })
        .execute();
    } catch (error) {
      this.logger.warn('Failed to update lastLogin:', error);
    }
  }

  async issueTokenPair(userId: string) {
    return this.generateTokens(userId);
  }

  async login(loginDto: LoginDto) {
    const user = await this.validateCredentials(loginDto.email, loginDto.password);
    await this.touchLastLogin(user.id);
    let tokens: { accessToken: string; refreshToken: string };
    try {
      tokens = await this.issueTokenPair(user.id);
    } catch (e) {
      this.logger.error(
        `JWT sign failed for user ${user.id}: ${e instanceof Error ? e.message : e}`,
        e instanceof Error ? e.stack : undefined,
      );
      throw e;
    }
    const fresh = await this.userRepository.findOne({ where: { id: user.id } });
    const u = fresh ?? user;
    return {
      ...tokens,
      user: await this.toPublicUserWithPartnersSafe(u),
      mustChangePassword: u.mustChangePassword === true,
    };
  }

  async validateUser(userId: string) {
    const user = await this.userRepository.findOne({
      where: { id: userId },
    });

    if (!user) {
      throw new UnauthorizedException('User not found');
    }
    if (user.accountStatus === UserAccountStatus.DELETED || user.deletedAt) {
      throw new UnauthorizedException('Account is deleted');
    }

    return this.toPublicUserWithPartnersSafe(user);
  }

  async updateProfile(
    userId: string,
    dto: {
      email?: string;
      firstName?: string;
      lastName?: string;
      phone?: string;
      bio?: string;
      language?: string;
      avatarUrl?: string;
      countryCode?: string;
      diverProfile?: Record<string, unknown>;
    },
  ): Promise<PublicUserWithPartners> {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) {
      throw new UnauthorizedException('User not found');
    }

    if (dto.email !== undefined) {
      const next = String(dto.email ?? '').trim().toLowerCase();
      if (!next) {
        throw new BadRequestException('email cannot be empty');
      }
      if (next !== (user.email || '').toLowerCase()) {
        const taken = await this.userRepository.findOne({
          where: { email: next },
        });
        if (taken && taken.id !== userId) {
          throw new ConflictException('Email already registered');
        }
        user.email = next;
      }
    }

    if (dto.firstName !== undefined) {
      const v = dto.firstName.trim();
      if (!v) {
        throw new BadRequestException('firstName cannot be empty');
      }
      user.firstName = v;
    }
    if (dto.lastName !== undefined) {
      const v = dto.lastName.trim();
      if (!v) {
        throw new BadRequestException('lastName cannot be empty');
      }
      user.lastName = v;
    }
    if (dto.phone !== undefined) {
      const v = String(dto.phone ?? '').trim();
      user.phone = v.length > 0 ? v : undefined;
    }
    if (dto.bio !== undefined) {
      const v = String(dto.bio ?? '').trim();
      user.bio = v.length > 0 ? v : null;
    }
    if (dto.language !== undefined) {
      const v = dto.language.trim().toLowerCase();
      user.language = v.length > 0 ? v.slice(0, 16) : 'en';
    }
    if (dto.avatarUrl !== undefined) {
      const v = String(dto.avatarUrl ?? '').trim();
      user.avatarUrl = v.length > 0 ? v.slice(0, 500) : undefined;
    }
    if (dto.countryCode !== undefined) {
      const v = String(dto.countryCode ?? '').trim().toUpperCase();
      user.countryCode = v.length > 0 ? v.slice(0, 8) : undefined;
    }
    if (dto.diverProfile !== undefined && isPlainObject(dto.diverProfile)) {
      const prev =
        user.diverProfile && isPlainObject(user.diverProfile)
          ? (user.diverProfile as Record<string, unknown>)
          : {};
      user.diverProfile = mergeDiverProfile(prev, dto.diverProfile);
    }

    await this.userRepository.save(user);
    return this.toPublicUserWithPartnersSafe(user);
  }

  async refreshToken(refreshToken: string) {
    try {
      const payload = this.jwtService.verify(refreshToken);
      const userRow = await this.userRepository.findOne({
        where: { id: payload.sub },
      });
      if (!userRow) {
        throw new UnauthorizedException('User not found');
      }
      if (userRow.accountStatus === UserAccountStatus.DELETED || userRow.deletedAt) {
        throw new UnauthorizedException('Account is deleted');
      }
      const user = await this.toPublicUserWithPartnersSafe(userRow);
      const tokens = await this.generateTokens(userRow.id);

      return {
        ...tokens,
        user,
        mustChangePassword: userRow.mustChangePassword === true,
      };
    } catch (error) {
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
  }

  async changePassword(
    userId: string,
    dto: { currentPassword: string; newPassword: string },
  ) {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user?.password) {
      throw new UnauthorizedException('User not found');
    }
    const ok = await bcrypt.compare(dto.currentPassword, user.password);
    if (!ok) {
      throw new UnauthorizedException('Invalid current password');
    }
    const hasLetter = /[a-zA-Z]/.test(dto.newPassword);
    const hasNumber = /\d/.test(dto.newPassword);
    if (!hasLetter || !hasNumber) {
      throw new BadRequestException(
        'New password must contain at least one letter and one number',
      );
    }
    user.password = await bcrypt.hash(dto.newPassword, 10);
    user.mustChangePassword = false;
    await this.userRepository.save(user);
    return { ok: true, user: await this.toPublicUserWithPartnersSafe(user) };
  }

  async forgotPassword(forgotPasswordDto: ForgotPasswordDto) {
    const { email } = forgotPasswordDto;

    // Find user by email
    const user = await this.userRepository.findOne({
      where: { email },
    });

    // Always return success message to prevent email enumeration
    // But only generate code if user exists
    if (user) {
      // Generate 6-digit verification code
      const resetCode = Math.floor(100000 + Math.random() * 900000).toString();
      const expiresAt = new Date();
      expiresAt.setMinutes(expiresAt.getMinutes() + 15); // Code expires in 15 minutes

      // Save reset code to database
      user.passwordResetCode = resetCode;
      user.passwordResetExpires = expiresAt;
      await this.userRepository.save(user);

      const isDevelopment = process.env.NODE_ENV !== 'production';
      const smtpConfigured =
        !!process.env.SMTP_HOST?.trim() &&
        !!process.env.SMTP_USER?.trim() &&
        !!process.env.SMTP_PASSWORD?.trim();

      if (isDevelopment && !smtpConfigured) {
        return {
          message: 'If an account with that email exists, a password reset code has been sent.',
          resetCode: resetCode,
          note: 'SMTP not configured - code returned for development purposes only',
        };
      }

      if (smtpConfigured) {
        try {
          await this.mailService.sendPasswordReset({
            to: user.email,
            code: resetCode,
            validMinutes: 15,
          });
        } catch (err) {
          this.logger.error(
            `sendPasswordReset failed for ${user.email}: ${err instanceof Error ? err.message : err}`,
          );
        }
      } else {
        this.logger.warn(
          `Password reset requested for ${user.email} but SMTP is not configured (set SMTP_HOST, SMTP_USER, SMTP_PASSWORD). Code stored in DB only.`,
        );
      }
    }

    return {
      message: 'If an account with that email exists, a password reset code has been sent.',
    };
  }

  async verifyResetCode(verifyResetCodeDto: VerifyResetCodeDto) {
    const { email, code } = verifyResetCodeDto;

    const user = await this.userRepository.findOne({
      where: { email },
    });

    if (!user) {
      throw new NotFoundException('User not found');
    }

    if (!user.passwordResetCode || user.passwordResetCode !== code) {
      throw new BadRequestException('Invalid verification code');
    }

    if (!user.passwordResetExpires || user.passwordResetExpires < new Date()) {
      throw new BadRequestException('Verification code has expired');
    }

    return {
      message: 'Verification code is valid',
      token: 'verified',
    };
  }

  async resetPassword(resetPasswordDto: ResetPasswordDto) {
    const { email, code, newPassword } = resetPasswordDto;

    const user = await this.userRepository.findOne({
      where: { email },
    });

    if (!user) {
      throw new NotFoundException('User not found');
    }

    if (!user.passwordResetCode || user.passwordResetCode !== code) {
      throw new BadRequestException('Invalid verification code');
    }

    if (!user.passwordResetExpires || user.passwordResetExpires < new Date()) {
      throw new BadRequestException('Verification code has expired');
    }

    // Hash new password
    const hashedPassword = await bcrypt.hash(newPassword, 10);

    // Update password and clear reset code
    user.password = hashedPassword;
    user.passwordResetCode = null;
    user.passwordResetExpires = null;
    await this.userRepository.save(user);

    return {
      message: 'Password has been reset successfully',
    };
  }

  private parseOAuthClientIds(
    multiKey: string,
    singleKey: string,
  ): string[] {
    const raw =
      this.configService.get<string>(multiKey)?.trim() ||
      this.configService.get<string>(singleKey)?.trim() ||
      '';
    if (!raw) {
      return [];
    }
    return raw
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }

  private syntheticAppleEmail(sub: string): string {
    const slug = Buffer.from(sub, 'utf8')
      .toString('base64url')
      .replace(/=+$/g, '');
    return `apple.oauth.${slug}@dive-hub.ru`;
  }

  private async findOrCreateOAuthUser(params: {
    provider: 'apple' | 'google';
    sub: string;
    emailHint?: string | null;
    firstNameHint?: string | null;
    lastNameHint?: string | null;
  }): Promise<User> {
    const { provider, sub } = params;

    let user = await this.userRepository.findOne({
      where:
        provider === 'apple' ? { appleSub: sub } : { googleSub: sub },
    });
    if (user) {
      await this.touchLastLogin(user.id);
      return user;
    }

    const emailNorm = params.emailHint?.toLowerCase().trim() || null;

    if (emailNorm) {
      user = await this.userRepository
        .createQueryBuilder('u')
        .where('LOWER(TRIM(u.email)) = :e', { e: emailNorm })
        .getOne();

      if (user) {
        if (provider === 'apple') {
          if (user.appleSub && user.appleSub !== sub) {
            throw new ConflictException(
              'This email is already linked to another Apple account',
            );
          }
          user.appleSub = sub;
        } else {
          if (user.googleSub && user.googleSub !== sub) {
            throw new ConflictException(
              'This email is already linked to another Google account',
            );
          }
          user.googleSub = sub;
        }
        user.emailVerified = true;
        await this.userRepository.save(user);
        await this.touchLastLogin(user.id);
        return user;
      }
    }

    let email = emailNorm ?? this.syntheticAppleEmail(sub);
    if (!emailNorm) {
      for (let i = 0; i < 8; i++) {
        const taken = await this.userRepository
          .createQueryBuilder('u')
          .where('LOWER(TRIM(u.email)) = LOWER(:e)', { e: email })
          .getOne();
        if (!taken) {
          break;
        }
        email = this.syntheticAppleEmail(`${sub}:${i}:${randomBytes(4).toString('hex')}`);
      }
    }

    const fn = (params.firstNameHint ?? '').trim() || 'User';
    const ln = (params.lastNameHint ?? '').trim() || '—';
    const randomPassword = await bcrypt.hash(randomBytes(48).toString('hex'), 10);

    const created = this.userRepository.create({
      email,
      password: randomPassword,
      firstName: fn,
      lastName: ln,
      role: 'DIVER_BASIC',
      emailVerified: true,
      mustChangePassword: false,
      ...(provider === 'apple' ? { appleSub: sub } : { googleSub: sub }),
    });

    const saved = await this.userRepository.save(created);
    await this.touchLastLogin(saved.id);
    return saved;
  }

  private async oauthLoginResponse(user: User) {
    const tokens = await this.issueTokenPair(user.id);
    const fresh = await this.userRepository.findOne({ where: { id: user.id } });
    const u = fresh ?? user;
    return {
      ...tokens,
      user: await this.toPublicUserWithPartnersSafe(u),
      mustChangePassword: u.mustChangePassword === true,
    };
  }

  async signInWithApple(dto: AppleAuthDto) {
    const audiences = this.parseOAuthClientIds(
      'APPLE_CLIENT_IDS',
      'APPLE_CLIENT_ID',
    );
    if (audiences.length === 0) {
      throw new ServiceUnavailableException(
        'Apple Sign In is not configured (set APPLE_CLIENT_ID or APPLE_CLIENT_IDS)',
      );
    }

    if (!dto.personalDataConsent) {
      throw new BadRequestException('Personal data processing consent is required');
    }
    const appleConsentText = dto.personalDataConsentText?.trim() ?? '';
    if (appleConsentText.length < 20) {
      throw new BadRequestException('Personal data consent text is required');
    }
    this.logger.log(
      `Apple OAuth personal data consent accepted; text length=${appleConsentText.length}`,
    );

    let payload: Awaited<ReturnType<typeof verifyAppleIdentityToken>>;
    try {
      payload = await verifyAppleIdentityToken(dto.idToken, audiences);
    } catch (e) {
      this.logger.warn(
        `Apple id_token verify failed: ${e instanceof Error ? e.message : e}`,
      );
      throw new UnauthorizedException('Invalid Apple identity token');
    }

    const emailFromToken =
      typeof payload.email === 'string' ? payload.email : undefined;
    const emailHint =
      dto.email?.trim() || emailFromToken?.trim() || null;

    const fn = dto.firstName?.trim() || undefined;
    const ln = dto.lastName?.trim() || undefined;

    const user = await this.findOrCreateOAuthUser({
      provider: 'apple',
      sub: payload.sub,
      emailHint,
      firstNameHint: fn ?? null,
      lastNameHint: ln ?? null,
    });

    return this.oauthLoginResponse(user);
  }

  async signInWithGoogle(dto: GoogleAuthDto) {
    const audiences = this.parseOAuthClientIds(
      'GOOGLE_CLIENT_IDS',
      'GOOGLE_CLIENT_ID',
    );
    if (audiences.length === 0) {
      throw new ServiceUnavailableException(
        'Google Sign In is not configured (set GOOGLE_CLIENT_ID or GOOGLE_CLIENT_IDS)',
      );
    }

    if (!dto.personalDataConsent) {
      throw new BadRequestException('Personal data processing consent is required');
    }
    const googleConsentText = dto.personalDataConsentText?.trim() ?? '';
    if (googleConsentText.length < 20) {
      throw new BadRequestException('Personal data consent text is required');
    }
    this.logger.log(
      `Google OAuth personal data consent accepted; text length=${googleConsentText.length}`,
    );

    let payload: Awaited<ReturnType<typeof verifyGoogleIdentityToken>>;
    try {
      payload = await verifyGoogleIdentityToken(dto.idToken, audiences);
    } catch (e) {
      this.logger.warn(
        `Google id_token verify failed: ${e instanceof Error ? e.message : e}`,
      );
      throw new UnauthorizedException('Invalid Google ID token');
    }

    const ev = payload.email_verified;
    const verified =
      ev === true || ev === 'true' || ev === '1';
    const emailFromToken =
      typeof payload.email === 'string' ? payload.email.trim() : '';
    if (!verified || !emailFromToken) {
      throw new UnauthorizedException(
        'Google account email is missing or not verified',
      );
    }

    const emailHint = emailFromToken.toLowerCase();

    const fn =
      dto.firstName?.trim() ||
      payload.given_name?.trim() ||
      undefined;
    const ln =
      dto.lastName?.trim() ||
      payload.family_name?.trim() ||
      undefined;

    const user = await this.findOrCreateOAuthUser({
      provider: 'google',
      sub: payload.sub,
      emailHint,
      firstNameHint: fn ?? null,
      lastNameHint: ln ?? null,
    });

    return this.oauthLoginResponse(user);
  }

  private async generateTokens(userId: string) {
    const payload = { sub: userId };

    const [accessToken, refreshToken] = await Promise.all([
      this.jwtService.signAsync(payload, { expiresIn: '1h' }),
      this.jwtService.signAsync(payload, { expiresIn: '7d' }),
    ]);

    return {
      accessToken,
      refreshToken,
    };
  }
}
