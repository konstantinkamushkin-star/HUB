import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { BuddySearch } from './entities/buddy-search.entity';
import { UpsertBuddySearchDto } from './dto/upsert-buddy-search.dto';
import { ListBuddySearchQueryDto } from './dto/list-buddy-search.dto';

function toPublicUser(user: User): Omit<User, 'password'> {
  const { password: _, ...rest } = user;
  return rest;
}

function normalizeDate(raw: string): string {
  const m = String(raw).trim().match(/^(\d{4}-\d{2}-\d{2})/);
  if (!m) {
    throw new BadRequestException('dateFrom/dateTo must be YYYY-MM-DD');
  }
  return m[1];
}

function normalizeList(raw?: string[]): string[] {
  if (!raw?.length) return [];
  return [
    ...new Set(
      raw
        .map((s) => String(s).trim())
        .filter((s) => s.length > 0)
        .slice(0, 20),
    ),
  ];
}

function placeTokens(place: string): string[] {
  return place
    .toLowerCase()
    .split(/[\s,/|-]+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 2);
}

function splitCsv(raw?: string): string[] {
  if (!raw?.trim()) return [];
  return [
    ...new Set(
      raw
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0),
    ),
  ];
}

@Injectable()
export class BuddySearchService {
  constructor(
    @InjectRepository(BuddySearch)
    private readonly searchRepository: Repository<BuddySearch>,
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
  ) {}

  async hasOpenSearch(userId: string): Promise<boolean> {
    const n = await this.searchRepository.count({
      where: { userId, status: 'open' },
    });
    return n > 0;
  }

  /** True if both users have open searches with overlapping place+dates. */
  async canMessageAsBuddyMatch(
    userId: string,
    peerId: string,
  ): Promise<boolean> {
    const [mine, peer] = await Promise.all([
      this.searchRepository.findOne({
        where: { userId, status: 'open' },
      }),
      this.searchRepository.findOne({
        where: { userId: peerId, status: 'open' },
      }),
    ]);
    if (!mine || !peer) return false;
    if (!this.datesOverlap(mine.dateFrom, mine.dateTo, peer.dateFrom, peer.dateTo)) {
      return false;
    }
    return this.placesOverlap(mine.place, peer.place);
  }

  async getMine(userId: string) {
    const row = await this.searchRepository.findOne({
      where: { userId, status: 'open' },
      relations: ['user'],
      order: { updatedAt: 'DESC' },
    });
    return row ? this.toDto(row) : null;
  }

  async upsert(userId: string, dto: UpsertBuddySearchDto) {
    const user = await this.userRepository.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    const dateFrom = normalizeDate(dto.dateFrom);
    const dateTo = normalizeDate(dto.dateTo);
    if (dateTo < dateFrom) {
      throw new BadRequestException('dateTo must be >= dateFrom');
    }

    let row = await this.searchRepository.findOne({
      where: { userId, status: 'open' },
    });
    if (!row) {
      row = this.searchRepository.create({ userId, status: 'open' });
    }

    row.place = dto.place.trim();
    row.dateFrom = dateFrom;
    row.dateTo = dateTo;
    row.certificationLevel = dto.certificationLevel?.trim() || null;
    row.diveCount =
      typeof dto.diveCount === 'number' && Number.isFinite(dto.diveCount)
        ? dto.diveCount
        : null;
    row.languages = normalizeList(dto.languages);
    row.interests = normalizeList(dto.interests);
    row.status = 'open';

    const saved = await this.searchRepository.save(row);
    saved.user = user;

    // Mark profile as looking for a buddy (discovery flag only).
    const prev = (user.diverProfile ?? {}) as Record<string, unknown>;
    user.diverProfile = { ...prev, lookingForBuddy: true };
    await this.userRepository.save(user);

    const matches = await this.findMatches(userId, saved);
    return {
      search: this.toDto(saved),
      matchCount: matches.length,
      matches,
    };
  }

  async listMatches(userId: string) {
    const mine = await this.searchRepository.findOne({
      where: { userId, status: 'open' },
    });
    if (!mine) {
      return {
        mine: null,
        matchCount: 0,
        matches: [] as ReturnType<BuddySearchService['toMatch']>[],
      };
    }
    const matches = await this.findMatches(userId, mine);
    return { mine: this.toDto(mine), matchCount: matches.length, matches };
  }

  /** All open questionnaires except the current user, with optional filters. */
  async listListings(userId: string, query: ListBuddySearchQueryDto = {}) {
    const mine = await this.searchRepository.findOne({
      where: { userId, status: 'open' },
    });

    const qb = this.searchRepository
      .createQueryBuilder('s')
      .leftJoinAndSelect('s.user', 'user')
      .where('s.status = :status', { status: 'open' })
      .andWhere('s.userId != :uid', { uid: userId })
      .orderBy('s.updatedAt', 'DESC');

    const dateFrom = query.dateFrom ? normalizeDate(query.dateFrom) : undefined;
    const dateTo = query.dateTo ? normalizeDate(query.dateTo) : undefined;
    if (dateFrom && dateTo && dateTo < dateFrom) {
      throw new BadRequestException('dateTo must be >= dateFrom');
    }
    if (dateFrom && dateTo) {
      qb.andWhere('s.dateFrom <= :to', { to: dateTo }).andWhere(
        's.dateTo >= :from',
        { from: dateFrom },
      );
    } else if (dateFrom) {
      qb.andWhere('s.dateTo >= :from', { from: dateFrom });
    } else if (dateTo) {
      qb.andWhere('s.dateFrom <= :to', { to: dateTo });
    }

    const cert = query.certificationLevel?.trim();
    if (cert) {
      qb.andWhere('LOWER(s.certificationLevel) = LOWER(:cert)', { cert });
    }

    let others = await qb.getMany();

    const place = query.place?.trim();
    if (place && place.length >= 2) {
      others = others.filter((o) => this.placesOverlap(place, o.place));
    }

    const languages = splitCsv(query.languages);
    if (languages.length) {
      const want = new Set(languages.map((l) => l.toLowerCase()));
      others = others.filter((o) =>
        (o.languages || []).some((l) => want.has(String(l).toLowerCase())),
      );
    }

    const interests = splitCsv(query.interests);
    if (interests.length) {
      const want = new Set(interests.map((i) => i.toLowerCase()));
      others = others.filter((o) =>
        (o.interests || []).some((i) => want.has(String(i).toLowerCase())),
      );
    }

    const matches = others
      .map((o) => (mine ? this.toMatch(o, mine) : this.toListing(o)))
      .sort((a, b) => b.score - a.score)
      .slice(0, 80);

    return {
      mine: mine ? this.toDto(mine) : null,
      matchCount: matches.length,
      matches,
    };
  }

  async list(userId: string) {
    return this.listListings(userId, {});
  }

  async remove(userId: string): Promise<void> {
    await this.close(userId);
  }

  async close(userId: string): Promise<void> {
    const row = await this.searchRepository.findOne({
      where: { userId, status: 'open' },
    });
    if (!row) return;
    row.status = 'closed';
    await this.searchRepository.save(row);
  }

  private async findMatches(userId: string, mine: BuddySearch) {
    const others = await this.searchRepository
      .createQueryBuilder('s')
      .leftJoinAndSelect('s.user', 'user')
      .where('s.status = :status', { status: 'open' })
      .andWhere('s.userId != :uid', { uid: userId })
      .andWhere('s.dateFrom <= :to', { to: mine.dateTo })
      .andWhere('s.dateTo >= :from', { from: mine.dateFrom })
      .orderBy('s.updatedAt', 'DESC')
      .take(100)
      .getMany();

    const scored = others
      .filter((o) => this.placesOverlap(mine.place, o.place))
      .map((o) => this.toMatch(o, mine))
      .sort((a, b) => b.score - a.score);

    return scored.slice(0, 50);
  }

  private datesOverlap(
    aFrom: string,
    aTo: string,
    bFrom: string,
    bTo: string,
  ): boolean {
    return aFrom <= bTo && aTo >= bFrom;
  }

  private placesOverlap(a: string, b: string): boolean {
    const aa = a.trim().toLowerCase();
    const bb = b.trim().toLowerCase();
    if (!aa || !bb) return false;
    if (aa === bb) return true;
    if (aa.includes(bb) || bb.includes(aa)) return true;
    const ta = placeTokens(aa);
    const tb = new Set(placeTokens(bb));
    return ta.some((t) => tb.has(t));
  }

  private toMatch(row: BuddySearch, mine: BuddySearch) {
    let score = 10; // base: place+time already matched
    const langHit = (row.languages || []).filter((l) =>
      (mine.languages || []).some(
        (m) => m.toLowerCase() === String(l).toLowerCase(),
      ),
    ).length;
    const interestHit = (row.interests || []).filter((i) =>
      (mine.interests || []).some(
        (m) => m.toLowerCase() === String(i).toLowerCase(),
      ),
    ).length;
    score += langHit * 3 + interestHit * 2;
    if (
      row.certificationLevel &&
      mine.certificationLevel &&
      row.certificationLevel.toLowerCase() ===
        mine.certificationLevel.toLowerCase()
    ) {
      score += 2;
    }
    return {
      score,
      search: this.toDto(row),
      user: row.user ? toPublicUser(row.user) : undefined,
    };
  }

  private toListing(row: BuddySearch) {
    return {
      score: 0,
      search: this.toDto(row),
      user: row.user ? toPublicUser(row.user) : undefined,
    };
  }

  private toDto(row: BuddySearch) {
    return {
      id: row.id,
      userId: row.userId,
      place: row.place,
      dateFrom: row.dateFrom,
      dateTo: row.dateTo,
      certificationLevel: row.certificationLevel,
      diveCount: row.diveCount,
      languages: row.languages ?? [],
      interests: row.interests ?? [],
      status: row.status,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    };
  }
}
