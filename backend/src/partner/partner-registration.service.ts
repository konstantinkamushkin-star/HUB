import { Injectable, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Point } from 'geojson';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { AdminVerificationRequestEntity } from '../admin/entities/admin-verification-request.entity';
import { AuditLogService } from '../admin/audit-log.service';
import { SubmitPartnerRegistrationDto } from './dto/submit-partner-registration.dto';
import { DiveCenterStatus, VerificationStatus } from '../common/statuses';

@Injectable()
export class PartnerRegistrationService {
  constructor(
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private readonly shopsRepo: Repository<ShopEntity>,
    @InjectRepository(AdminVerificationRequestEntity)
    private readonly requestsRepo: Repository<AdminVerificationRequestEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  async submit(dto: SubmitPartnerRegistrationDto) {
    if (dto.kind === 'dive_center') {
      if (dto.latitude == null || dto.longitude == null) {
        throw new BadRequestException('Укажите широту и долготу для дайв-центра');
      }
      return this.createDiveCenter(dto);
    }

    const shopType = dto.shopType ?? 'offline';
    if (shopType === 'offline' && (dto.latitude == null || dto.longitude == null)) {
      throw new BadRequestException('Для офлайн-магазина укажите координаты');
    }

    return this.createShop(dto, shopType);
  }

  private async createDiveCenter(dto: SubmitPartnerRegistrationDto) {
    const lat = dto.latitude!;
    const lng = dto.longitude!;
    const location: Point = { type: 'Point', coordinates: [lng, lat] };

    const center = this.centersRepo.create({
      name: dto.name.trim(),
      description: dto.description?.trim() ?? '',
      location,
      country: dto.country.trim(),
      city: dto.city.trim(),
      address: dto.address?.trim() ?? '',
      email: dto.contactEmail.trim(),
      phone: dto.contactPhone.trim(),
      website: dto.website?.trim() || null,
      status: DiveCenterStatus.PENDING,
      verification_status: VerificationStatus.PENDING,
      is_active: false,
    });
    const saved = await this.centersRepo.save(center);

    const verification = await this.createVerificationRequest({
      targetType: 'dive_center',
      targetId: saved.id,
      documents: this.snapshotDocuments(dto, { entityLabel: 'dive_center' }),
    });

    return {
      message:
        'Заявка принята. После проверки супер-администратором организация будет активирована в каталоге.',
      diveCenterId: saved.id,
      verificationRequestId: verification.id,
    };
  }

  private async createShop(dto: SubmitPartnerRegistrationDto, shopType: 'offline' | 'online') {
    const lat = dto.latitude;
    const lng = dto.longitude;
    const location: Point | null =
      lat != null && lng != null
        ? { type: 'Point', coordinates: [lng, lat] }
        : null;

    const shop = this.shopsRepo.create({
      name: dto.name.trim(),
      description: dto.description?.trim() ?? null,
      type: shopType,
      country: dto.country.trim(),
      city: dto.city.trim(),
      address: dto.address?.trim() ?? null,
      email: dto.contactEmail.trim(),
      phone: dto.contactPhone.trim(),
      website: dto.website?.trim() || null,
      location,
      verification_status: VerificationStatus.PENDING,
      is_active: false,
    });
    const saved = await this.shopsRepo.save(shop);

    const verification = await this.createVerificationRequest({
      targetType: 'shop',
      targetId: saved.id,
      documents: this.snapshotDocuments(dto, { entityLabel: 'shop', shopType }),
    });

    return {
      message:
        'Заявка принята. После проверки супер-администратором магазин будет активирован в каталоге.',
      shopId: saved.id,
      verificationRequestId: verification.id,
    };
  }

  private snapshotDocuments(
    dto: SubmitPartnerRegistrationDto,
    extra: Record<string, unknown>,
  ): Record<string, unknown> {
    return {
      source: 'public_partner_registration',
      submittedAt: new Date().toISOString(),
      kind: dto.kind,
      name: dto.name,
      description: dto.description ?? null,
      contactEmail: dto.contactEmail,
      contactPhone: dto.contactPhone,
      country: dto.country,
      city: dto.city,
      address: dto.address ?? null,
      website: dto.website ?? null,
      shopType: dto.shopType ?? null,
      latitude: dto.latitude ?? null,
      longitude: dto.longitude ?? null,
      personalDataConsent: dto.personalDataConsent,
      personalDataConsentText: dto.personalDataConsentText,
      personalDataConsentAcceptedAt: new Date().toISOString(),
      ...extra,
    };
  }

  private async createVerificationRequest(params: {
    targetType: string;
    targetId: string;
    documents: Record<string, unknown>;
  }) {
    const existing = await this.requestsRepo.findOne({
      where: { targetType: params.targetType, targetId: params.targetId },
      order: { createdAt: 'DESC' },
    });
    const row = this.requestsRepo.create({
      targetType: params.targetType,
      targetId: params.targetId,
      status: 'pending',
      attemptNumber: (existing?.attemptNumber ?? 0) + 1,
      documents: params.documents,
      decisionNote: null,
      handledByAdminId: null,
      history: [],
    });
    const saved = await this.requestsRepo.save(row);
    await this.auditLogService.write({
      adminId: null,
      action: 'public.partner_registration',
      targetType: params.targetType,
      targetId: params.targetId,
      after: { requestId: saved.id, status: saved.status, attempt: saved.attemptNumber },
      ip: null,
      device: null,
      correlationId: null,
    });
    return saved;
  }
}
