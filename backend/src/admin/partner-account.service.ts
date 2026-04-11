import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcryptjs';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { MailService } from '../mail/mail.service';

function randomPartnerPassword(): string {
  // ВРЕМЕННО для тестов: фиксированный пароль вместо случайного.
  return '1234qwerty';
}

@Injectable()
export class PartnerAccountService {
  private readonly logger = new Logger(PartnerAccountService.name);

  constructor(
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private readonly shopsRepo: Repository<ShopEntity>,
    private readonly mail: MailService,
  ) {}

  /**
   * После verified: создать/обновить пользователя, привязать к центру/магазину, отправить пароль на contactEmail.
   * Роль: DIVE_CENTER_ADMIN для дайв-центра, SHOP_ADMIN для магазина.
   */
  async provisionPartnerLogin(params: {
    targetType: string;
    targetId: string;
    documents: Record<string, unknown> | null;
  }): Promise<void> {
    if (params.targetType !== 'dive_center' && params.targetType !== 'shop') {
      return;
    }

    const doc = params.documents ?? {};
    const emailRaw = doc['contactEmail'];
    const email =
      typeof emailRaw === 'string' ? emailRaw.trim().toLowerCase() : '';
    if (!email || !email.includes('@')) {
      this.logger.warn(
        `Partner verify: нет contactEmail в documents, аккаунт не создан (target=${params.targetType}/${params.targetId})`,
      );
      return;
    }

    const nameRaw = typeof doc['name'] === 'string' ? doc['name'].trim() : 'Партнёр';
    const parts = nameRaw.split(/\s+/).filter(Boolean);
    const firstName = (parts[0] ?? 'Dive').slice(0, 100);
    const lastName =
      parts.length > 1 ? parts.slice(1).join(' ').slice(0, 100) : 'Center';

    const plain = randomPartnerPassword();
    const hash = await bcrypt.hash(plain, 10);

    let user = await this.usersRepo.findOne({ where: { email } });
    const partnerRole =
      params.targetType === 'shop' ? 'SHOP_ADMIN' : 'DIVE_CENTER_ADMIN';

    if (user) {
      user.password = hash;
      user.role = partnerRole;
      user.mustChangePassword = true;
      if (!user.firstName?.trim()) user.firstName = firstName;
      if (!user.lastName?.trim()) user.lastName = lastName;
      user.emailVerified = true;
      user = await this.usersRepo.save(user);
    } else {
      user = await this.usersRepo.save(
        this.usersRepo.create({
          email,
          password: hash,
          firstName,
          lastName,
          role: partnerRole,
          mustChangePassword: true,
          emailVerified: true,
        }),
      );
    }

    if (params.targetType === 'dive_center') {
      await this.centersRepo.update(
        { id: params.targetId },
        { owner_id: user.id },
      );
    } else {
      await this.shopsRepo.update({ id: params.targetId }, { owner_id: user.id });
    }

    try {
      await this.mail.sendPartnerWelcome({
        to: email,
        temporaryPassword: plain,
        businessName: nameRaw,
      });
    } catch (e) {
      this.logger.error(
        `Не удалось отправить письмо партнёру ${email}: ${e instanceof Error ? e.message : e}`,
      );
    }
  }
}
