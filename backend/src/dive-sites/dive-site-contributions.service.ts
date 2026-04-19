import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, EntityManager, Repository } from 'typeorm';
import { DiveSiteContributionEntity } from './entities/dive-site-contribution.entity';
import { DiveSiteEntity } from './entities/dive-site.entity';
import {
  CreateDiveSiteContributionDto,
  CreateDiveSiteContributionTypeDto,
} from './dto/create-dive-site-contribution.dto';
import { DiveSiteStatus } from '../common/statuses';

const PATCH_KEYS = new Set([
  'name',
  'description',
  'localized_name',
  'localized_description',
  'country',
  'region',
  'address',
  'site_types',
  'difficulty_level',
  'depth_min',
  'depth_max',
  'water_temp_min',
  'water_temp_max',
  'seasonality',
  'access_type',
  'price_from',
  'marine_life',
  'photo_urls',
  'video_urls',
  'ai_summary',
]);

@Injectable()
export class DiveSiteContributionsService {
  constructor(
    @InjectRepository(DiveSiteContributionEntity)
    private readonly contributionRepo: Repository<DiveSiteContributionEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly diveSiteRepo: Repository<DiveSiteEntity>,
    private readonly dataSource: DataSource,
  ) {}

  async create(
    userId: string,
    dto: CreateDiveSiteContributionDto,
  ): Promise<DiveSiteContributionEntity> {
    const proposed = dto.proposedData ?? {};
    if (dto.type === CreateDiveSiteContributionTypeDto.correction) {
      if (!dto.diveSiteId) {
        throw new BadRequestException('diveSiteId is required for corrections');
      }
      const exists = await this.diveSiteRepo.findOne({
        where: { id: dto.diveSiteId },
      });
      if (!exists) {
        throw new NotFoundException('Dive site not found');
      }
      this.assertCorrectionHasPayload(proposed, dto.message);
    } else {
      this.validateNewSiteProposal(proposed);
    }

    const row = this.contributionRepo.create({
      contribution_type:
        dto.type === CreateDiveSiteContributionTypeDto.correction
          ? 'correction'
          : 'new_site',
      dive_site_id:
        dto.type === CreateDiveSiteContributionTypeDto.correction
          ? dto.diveSiteId!
          : null,
      submitter_user_id: userId,
      proposed_data: proposed,
      message: dto.message?.trim() ?? null,
      status: 'pending',
    });
    return this.contributionRepo.save(row);
  }

  async listMine(
    userId: string,
    limit = 30,
  ): Promise<DiveSiteContributionEntity[]> {
    const take = Math.min(Math.max(limit, 1), 100);
    return this.contributionRepo.find({
      where: { submitter_user_id: userId },
      order: { created_at: 'DESC' },
      take,
    });
  }

  async listAdmin(
    status?: string,
    limit = 50,
  ): Promise<
    Array<
      DiveSiteContributionEntity & { submitterEmail: string | null }
    >
  > {
    const take = Math.min(Math.max(limit, 1), 200);
    const rows =
      status && ['pending', 'approved', 'rejected'].includes(status)
        ? await this.contributionRepo.find({
            where: {
              status: status as DiveSiteContributionEntity['status'],
            },
            order: { created_at: 'DESC' },
            take,
          })
        : await this.contributionRepo.find({
            order: { created_at: 'DESC' },
            take,
          });
    const emails = await this.loadSubmitterEmails(
      rows.map((r) => r.submitter_user_id),
    );
    return rows.map((c) =>
      Object.assign(c, {
        submitterEmail: emails.get(c.submitter_user_id) ?? null,
      }),
    );
  }

  private async loadSubmitterEmails(
    ids: string[],
  ): Promise<Map<string, string>> {
    const uniq = [...new Set(ids)].filter(Boolean);
    const map = new Map<string, string>();
    if (!uniq.length) return map;
    const rows: { id: string; email: string }[] = await this.dataSource.query(
      `SELECT id, email FROM users WHERE id = ANY($1::uuid[])`,
      [uniq],
    );
    for (const r of rows) {
      map.set(r.id, r.email);
    }
    return map;
  }

  async approve(id: string, adminId: string): Promise<{ success: boolean }> {
    await this.dataSource.transaction(async (manager) => {
      const c = await manager.findOne(DiveSiteContributionEntity, {
        where: { id },
      });
      if (!c) {
        throw new NotFoundException('Contribution not found');
      }
      if (c.status !== 'pending') {
        throw new BadRequestException('Contribution already processed');
      }

      if (c.contribution_type === 'correction') {
        if (!c.dive_site_id) {
          throw new BadRequestException('Missing dive site for correction');
        }
        await this.applyCorrection(
          manager,
          c.dive_site_id,
          c.proposed_data,
        );
      } else {
        await this.insertNewSite(manager, c.proposed_data);
      }

      c.status = 'approved';
      c.reviewed_by = adminId;
      c.reviewed_at = new Date();
      c.rejection_reason = null;
      await manager.save(c);
    });
    return { success: true };
  }

  async reject(
    id: string,
    adminId: string,
    reason?: string,
  ): Promise<{ success: boolean }> {
    const c = await this.contributionRepo.findOne({ where: { id } });
    if (!c) {
      throw new NotFoundException('Contribution not found');
    }
    if (c.status !== 'pending') {
      throw new BadRequestException('Contribution already processed');
    }
    c.status = 'rejected';
    c.reviewed_by = adminId;
    c.reviewed_at = new Date();
    c.rejection_reason = reason?.trim() ?? null;
    await this.contributionRepo.save(c);
    return { success: true };
  }

  private assertCorrectionHasPayload(
    proposed: Record<string, unknown>,
    message?: string,
  ): void {
    const hasPatch = [...PATCH_KEYS, 'latitude', 'longitude', 'lat', 'lng', 'lon'].some(
      (k) =>
        Object.prototype.hasOwnProperty.call(proposed, k) &&
        proposed[k] !== undefined &&
        proposed[k] !== null,
    );
    const msg = message?.trim();
    if (!hasPatch && !msg) {
      throw new BadRequestException(
        'Describe the inaccuracy or provide corrected fields in proposedData',
      );
    }
  }

  private validateNewSiteProposal(p: Record<string, unknown>): void {
    const name = p.name;
    const lat = p.latitude ?? p.lat;
    const lng = p.longitude ?? p.lng ?? p.lon;
    if (typeof name !== 'string' || !name.trim()) {
      throw new BadRequestException('New site: name is required in proposedData');
    }
    const latN = Number(lat);
    const lngN = Number(lng);
    if (!Number.isFinite(latN) || !Number.isFinite(lngN)) {
      throw new BadRequestException(
        'New site: valid latitude and longitude are required in proposedData',
      );
    }
    if (latN < -90 || latN > 90 || lngN < -180 || lngN > 180) {
      throw new BadRequestException('Invalid coordinates');
    }
  }

  private async applyCorrection(
    manager: EntityManager,
    siteId: string,
    proposed: Record<string, unknown>,
  ): Promise<void> {
    const latRaw = proposed.latitude ?? proposed.lat;
    const lngRaw = proposed.longitude ?? proposed.lng ?? proposed.lon;
    let latN: number | undefined;
    let lngN: number | undefined;
    if (latRaw !== undefined && latRaw !== null) {
      latN = Number(latRaw);
    }
    if (lngRaw !== undefined && lngRaw !== null) {
      lngN = Number(lngRaw);
    }
    if (
      (latN !== undefined && !Number.isFinite(latN)) ||
      (lngN !== undefined && !Number.isFinite(lngN))
    ) {
      throw new BadRequestException('Invalid coordinates in proposedData');
    }
    if (latN !== undefined && lngN !== undefined) {
      await manager.query(
        `UPDATE dive_sites SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography WHERE id = $3`,
        [lngN, latN, siteId],
      );
    }

    const site = await manager.findOne(DiveSiteEntity, { where: { id: siteId } });
    if (!site) {
      throw new NotFoundException('Dive site not found');
    }

    const mutable = site as unknown as Record<string, unknown>;
    for (const key of PATCH_KEYS) {
      if (
        !Object.prototype.hasOwnProperty.call(proposed, key) ||
        proposed[key] === undefined
      ) {
        continue;
      }
      mutable[key] = proposed[key];
    }
    await manager.save(site);
  }

  private async insertNewSite(
    manager: EntityManager,
    proposed: Record<string, unknown>,
  ): Promise<void> {
    const name = String(proposed.name).trim();
    const lat = Number(proposed.latitude ?? proposed.lat);
    const lng = Number(proposed.longitude ?? proposed.lng ?? proposed.lon);
    const description =
      typeof proposed.description === 'string' ? proposed.description : null;
    const country =
      typeof proposed.country === 'string' ? proposed.country : null;
    const region =
      typeof proposed.region === 'string' ? proposed.region : null;
    const address =
      typeof proposed.address === 'string' ? proposed.address : null;
    const siteTypes = Array.isArray(proposed.site_types)
      ? (proposed.site_types as unknown[]).map(String)
      : ['reef'];
    const difficulty =
      typeof proposed.difficulty_level === 'number'
        ? proposed.difficulty_level
        : Number(proposed.difficulty_level) || 2;
    const depthMin =
      proposed.depth_min !== undefined && proposed.depth_min !== null
        ? Number(proposed.depth_min)
        : 5;
    const depthMax =
      proposed.depth_max !== undefined && proposed.depth_max !== null
        ? Number(proposed.depth_max)
        : 30;
    const waterTempMin =
      proposed.water_temp_min !== undefined && proposed.water_temp_min !== null
        ? Number(proposed.water_temp_min)
        : null;
    const waterTempMax =
      proposed.water_temp_max !== undefined && proposed.water_temp_max !== null
        ? Number(proposed.water_temp_max)
        : null;
    const seasonality =
      proposed.seasonality && typeof proposed.seasonality === 'object'
        ? proposed.seasonality
        : null;
    const accessType = Array.isArray(proposed.access_type)
      ? (proposed.access_type as unknown[]).map(String)
      : ['boat'];
    const priceFrom =
      proposed.price_from !== undefined && proposed.price_from !== null
        ? Number(proposed.price_from)
        : null;
    const marineLife = Array.isArray(proposed.marine_life)
      ? (proposed.marine_life as unknown[]).map(String)
      : [];
    const photoUrls = Array.isArray(proposed.photo_urls)
      ? (proposed.photo_urls as unknown[]).map(String)
      : [];
    const videoUrls = Array.isArray(proposed.video_urls)
      ? (proposed.video_urls as unknown[]).map(String)
      : [];
    const aiSummary =
      typeof proposed.ai_summary === 'string' ? proposed.ai_summary : null;

    await manager.query(
      `
      INSERT INTO dive_sites (
        name, description, location, country, region, address,
        site_types, difficulty_level, depth_min, depth_max,
        water_temp_min, water_temp_max, seasonality, access_type, price_from,
        average_rating, review_count, photo_urls, video_urls, marine_life,
        is_active, status, ai_summary, affiliated_centers
      ) VALUES (
        $1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography, $5, $6, $7,
        $8::text[], $9, $10, $11, $12, $13, $14::jsonb, $15::text[], $16,
        0, 0, $17::text[], $18::text[], $19::text[],
        true, $20, $21, '{}'::uuid[]
      )
    `,
      [
        name,
        description,
        lng,
        lat,
        country,
        region,
        address,
        siteTypes,
        difficulty,
        depthMin,
        depthMax,
        waterTempMin,
        waterTempMax,
        seasonality,
        accessType,
        priceFrom,
        photoUrls,
        videoUrls,
        marineLife,
        DiveSiteStatus.PUBLISHED,
        aiSummary,
      ],
    );
  }
}
