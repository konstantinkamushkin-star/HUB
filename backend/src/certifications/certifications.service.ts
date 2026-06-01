import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { FriendsService } from '../friends/friends.service';
import { privacyAllows } from '../common/diver-profile.util';
import {
  CertificationVerificationStatus,
  UserCertificationEntity,
} from './entities/user-certification.entity';
import { CreateCertificationDto } from './dto/create-certification.dto';
import { UpdateCertificationDto } from './dto/update-certification.dto';

export type CertificationResponse = {
  id: string;
  agency: string;
  level: string;
  cardImageUrl: string | null;
  issueDate: string | null;
  instructorNumber: string | null;
  certificateNumber: string | null;
  verificationStatus: CertificationVerificationStatus;
};

function toResponse(row: UserCertificationEntity): CertificationResponse {
  return {
    id: row.id,
    agency: row.agency,
    level: row.level,
    cardImageUrl: row.cardImageUrl,
    issueDate: row.issueDate ? row.issueDate.toISOString() : null,
    instructorNumber: row.instructorNumber,
    certificateNumber: row.certificateNumber,
    verificationStatus: row.verificationStatus,
  };
}

function parseIssueDate(raw: string): Date {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) {
    return new Date();
  }
  return d;
}

function sameCalendarDay(a: Date, b: Date): boolean {
  return (
    a.getUTCFullYear() === b.getUTCFullYear() &&
    a.getUTCMonth() === b.getUTCMonth() &&
    a.getUTCDate() === b.getUTCDate()
  );
}

function normalizeCertNumber(raw: string | null | undefined): string | null {
  const trimmed = raw?.trim() ?? '';
  if (!trimmed) return null;
  return trimmed.replace(/^#+/, '').toUpperCase();
}

@Injectable()
export class CertificationsService {
  constructor(
    @InjectRepository(UserCertificationEntity)
    private readonly repo: Repository<UserCertificationEntity>,
    @InjectRepository(User)
    private readonly userRepo: Repository<User>,
    private readonly friendsService: FriendsService,
  ) {}

  private assertOwner(userId: string, currentUserId: string) {
    if (userId !== currentUserId) {
      throw new ForbiddenException('You can only manage your own certifications');
    }
  }

  private async assertCanViewCerts(
    targetUserId: string,
    viewerId: string,
  ): Promise<void> {
    if (targetUserId === viewerId) {
      return;
    }
    const friendIds = await this.friendsService.listFriendUserIds(viewerId);
    if (!friendIds.includes(targetUserId)) {
      throw new ForbiddenException('Certifications are visible to friends only');
    }
    const user = await this.userRepo.findOne({ where: { id: targetUserId } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (!privacyAllows(user, 'showCertificationLevel', true)) {
      throw new ForbiddenException('User hid certification details');
    }
  }

  async list(userId: string, currentUserId: string): Promise<CertificationResponse[]> {
    await this.assertCanViewCerts(userId, currentUserId);
    const rows = await this.repo.find({
      where: { userId },
      order: { issueDate: 'DESC', createdAt: 'DESC' },
    });
    return rows.map(toResponse);
  }

  async create(
    userId: string,
    currentUserId: string,
    dto: CreateCertificationDto,
  ): Promise<CertificationResponse> {
    this.assertOwner(userId, currentUserId);
    await this.assertNotDuplicate(userId, dto);
    const status =
      dto.verificationStatus && ['PENDING', 'VERIFIED', 'REJECTED'].includes(dto.verificationStatus)
        ? dto.verificationStatus
        : 'PENDING';
    const row = this.repo.create({
      userId,
      agency: dto.agency.trim(),
      level: dto.level.trim(),
      cardImageUrl: dto.cardImageUrl?.trim() || null,
      issueDate: parseIssueDate(dto.issueDate),
      instructorNumber: dto.instructorNumber?.trim() || null,
      certificateNumber: normalizeCertNumber(dto.certificateNumber),
      verificationStatus: status,
    });
    const saved = await this.repo.save(row);
    return toResponse(saved);
  }

  private async assertNotDuplicate(userId: string, dto: CreateCertificationDto): Promise<void> {
    const certNumber = normalizeCertNumber(dto.certificateNumber);
    if (certNumber) {
      const byNumber = await this.repo.findOne({
        where: { userId, certificateNumber: certNumber },
      });
      if (byNumber) {
        throw new ConflictException('Certification with this certificate number already exists');
      }
      return;
    }

    const agency = dto.agency.trim();
    const level = dto.level.trim();
    const issueDate = parseIssueDate(dto.issueDate);
    const candidates = await this.repo.find({ where: { userId, agency, level } });
    if (candidates.some((row) => row.issueDate && sameCalendarDay(row.issueDate, issueDate))) {
      throw new ConflictException(
        'A certification with the same organization, level, and issue date already exists',
      );
    }
  }

  async update(
    certificationId: string,
    currentUserId: string,
    dto: UpdateCertificationDto,
  ): Promise<CertificationResponse> {
    const row = await this.repo.findOne({ where: { id: certificationId } });
    if (!row) {
      throw new NotFoundException('Certification not found');
    }
    this.assertOwner(row.userId, currentUserId);

    const nextAgency = dto.agency !== undefined ? dto.agency.trim() : row.agency;
    const nextLevel = dto.level !== undefined ? dto.level.trim() : row.level;
    const nextIssueDate =
      dto.issueDate !== undefined ? parseIssueDate(dto.issueDate) : row.issueDate;
    const nextCertNumber =
      dto.certificateNumber !== undefined
        ? normalizeCertNumber(dto.certificateNumber)
        : row.certificateNumber;

    await this.assertNotDuplicateOnUpdate(row.userId, certificationId, {
      agency: nextAgency,
      level: nextLevel,
      issueDate: nextIssueDate,
      certificateNumber: nextCertNumber,
    });

    if (dto.agency !== undefined) row.agency = nextAgency;
    if (dto.level !== undefined) row.level = nextLevel;
    if (dto.issueDate !== undefined) row.issueDate = nextIssueDate;
    if (dto.instructorNumber !== undefined) {
      row.instructorNumber = dto.instructorNumber?.trim() || null;
    }
    if (dto.cardImageUrl !== undefined) {
      const trimmed = dto.cardImageUrl?.trim() ?? '';
      row.cardImageUrl = trimmed.length > 0 ? trimmed : null;
    }
    if (dto.certificateNumber !== undefined) {
      row.certificateNumber = nextCertNumber;
    }
    const saved = await this.repo.save(row);
    return toResponse(saved);
  }

  private async assertNotDuplicateOnUpdate(
    userId: string,
    certificationId: string,
    next: {
      agency: string;
      level: string;
      issueDate: Date | null;
      certificateNumber: string | null;
    },
  ): Promise<void> {
    if (next.certificateNumber) {
      const byNumber = await this.repo.findOne({
        where: { userId, certificateNumber: next.certificateNumber },
      });
      if (byNumber && byNumber.id !== certificationId) {
        throw new ConflictException('Certification with this certificate number already exists');
      }
      return;
    }

    const candidates = await this.repo.find({
      where: { userId, agency: next.agency, level: next.level },
    });
    if (
      next.issueDate &&
      candidates.some(
        (row) =>
          row.id !== certificationId &&
          row.issueDate &&
          sameCalendarDay(row.issueDate, next.issueDate!),
      )
    ) {
      throw new ConflictException(
        'A certification with the same organization, level, and issue date already exists',
      );
    }
  }

  async delete(certificationId: string, currentUserId: string): Promise<void> {
    const row = await this.repo.findOne({ where: { id: certificationId } });
    if (!row) {
      throw new NotFoundException('Certification not found');
    }
    this.assertOwner(row.userId, currentUserId);
    await this.repo.remove(row);
  }
}
