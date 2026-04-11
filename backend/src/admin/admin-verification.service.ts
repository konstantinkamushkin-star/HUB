import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AdminVerificationRequestEntity } from './entities/admin-verification-request.entity';
import { CreateVerificationRequestDto } from './dto/create-verification-request.dto';
import { UpdateVerificationRequestDto } from './dto/update-verification-request.dto';
import { AuditLogService } from './audit-log.service';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { User } from '../users/entities/user.entity';
import { VerificationStatus } from '../common/statuses';
import { PartnerAccountService } from './partner-account.service';

@Injectable()
export class AdminVerificationService {
  constructor(
    @InjectRepository(AdminVerificationRequestEntity)
    private readonly requestsRepo: Repository<AdminVerificationRequestEntity>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(ShopEntity)
    private readonly shopsRepo: Repository<ShopEntity>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    private readonly auditLogService: AuditLogService,
    private readonly partnerAccount: PartnerAccountService,
  ) {}

  list(limit = 100) {
    return this.requestsRepo.find({
      order: { createdAt: 'DESC' },
      take: Math.min(Math.max(limit, 1), 500),
    });
  }

  async create(dto: CreateVerificationRequestDto, actor: any) {
    const existing = await this.requestsRepo.findOne({
      where: { targetType: dto.targetType, targetId: dto.targetId },
      order: { createdAt: 'DESC' },
    });
    const row = this.requestsRepo.create({
      targetType: dto.targetType,
      targetId: dto.targetId,
      status: 'pending',
      attemptNumber: (existing?.attemptNumber ?? 0) + 1,
      documents: dto.documents ?? null,
      decisionNote: null,
      handledByAdminId: null,
      history: [],
    });
    const saved = await this.requestsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.verification_request.create',
      targetType: dto.targetType,
      targetId: dto.targetId,
      after: { requestId: saved.id, status: saved.status, attempt: saved.attemptNumber },
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }

  async update(id: string, dto: UpdateVerificationRequestDto, actor: any) {
    const row = await this.requestsRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Verification request not found');
    const before = { status: row.status, decisionNote: row.decisionNote };
    row.status = dto.status;
    row.decisionNote = dto.decisionNote ?? row.decisionNote;
    row.handledByAdminId = actor?.adminId ?? null;
    row.history = [
      ...(row.history ?? []),
      {
        at: new Date().toISOString(),
        adminId: actor?.adminId ?? null,
        status: row.status,
        decisionNote: row.decisionNote ?? null,
      },
    ];

    // Apply status to known entities
    if (row.targetType === 'dive_center') {
      const center = await this.centersRepo.findOne({ where: { id: row.targetId } });
      if (center) {
        if (dto.status === 'verified') center.verification_status = VerificationStatus.VERIFIED;
        if (dto.status === 'rejected') center.verification_status = VerificationStatus.REJECTED;
        if (dto.status === 'revoked') center.verification_status = VerificationStatus.REVOKED;
        if (dto.status === 'pending' || dto.status === 'more_info') center.verification_status = VerificationStatus.PENDING;
        if (dto.status === 'verified') center.is_active = true;
        if (dto.status === 'rejected' || dto.status === 'revoked') center.is_active = false;
        await this.centersRepo.save(center);
      }
    }
    if (row.targetType === 'shop') {
      const shop = await this.shopsRepo.findOne({ where: { id: row.targetId } });
      if (shop) {
        if (dto.status === 'verified') shop.verification_status = VerificationStatus.VERIFIED;
        if (dto.status === 'rejected') shop.verification_status = VerificationStatus.REJECTED;
        if (dto.status === 'revoked') shop.verification_status = VerificationStatus.REVOKED;
        if (dto.status === 'pending' || dto.status === 'more_info') shop.verification_status = VerificationStatus.PENDING;
        if (dto.status === 'verified') shop.is_active = true;
        if (dto.status === 'rejected' || dto.status === 'revoked') shop.is_active = false;
        await this.shopsRepo.save(shop);
      }
    }
    if (row.targetType === 'user') {
      const user = await this.usersRepo.findOne({ where: { id: row.targetId } });
      if (user) {
        if (dto.status === 'verified') user.verificationStatus = VerificationStatus.VERIFIED;
        if (dto.status === 'rejected') user.verificationStatus = VerificationStatus.REJECTED;
        if (dto.status === 'revoked') user.verificationStatus = VerificationStatus.REVOKED;
        if (dto.status === 'pending' || dto.status === 'more_info') user.verificationStatus = VerificationStatus.PENDING;
        await this.usersRepo.save(user);
      }
    }

    if (dto.status === 'verified') {
      const docs =
        row.documents && typeof row.documents === 'object' && !Array.isArray(row.documents)
          ? (row.documents as Record<string, unknown>)
          : null;
      await this.partnerAccount.provisionPartnerLogin({
        targetType: row.targetType,
        targetId: row.targetId,
        documents: docs,
      });
    }

    const saved = await this.requestsRepo.save(row);
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action: 'admin.verification_request.update',
      targetType: row.targetType,
      targetId: row.targetId,
      before,
      after: { status: saved.status, decisionNote: saved.decisionNote },
      reason: dto.decisionNote ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
    return saved;
  }
}
