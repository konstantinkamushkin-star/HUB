import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { DiveCentersService } from './dive-centers.service';

@Injectable()
export class CenterGearService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
    private readonly diveCenters: DiveCentersService,
  ) {}

  async list(centerId: string, actorId: string, actorRole?: string) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const rows = await this.dataSource.query(
      `
      SELECT id, dive_center_id AS "diveCenterId", name, category, manufacturer, status, "condition" AS condition,
             payload, created_at AS "createdAt", updated_at AS "updatedAt"
      FROM center_gear_items
      WHERE dive_center_id = $1::uuid
      ORDER BY updated_at DESC
      `,
      [centerId],
    );
    return rows.map((r: Record<string, unknown>) => this.mapRow(r));
  }

  async create(
    centerId: string,
    body: {
      name: string;
      category?: string;
      manufacturer?: string | null;
      status?: string;
      condition?: string;
    },
    actorId: string,
    actorRole?: string,
  ) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const name = body.name?.trim();
    if (!name) {
      throw new BadRequestException('name is required');
    }
    const rows = await this.dataSource.query(
      `
      INSERT INTO center_gear_items (
        dive_center_id, name, category, manufacturer, status, "condition", payload
      ) VALUES ($1::uuid, $2, $3, $4, $5, $6, '{}'::jsonb)
      RETURNING id, dive_center_id AS "diveCenterId", name, category, manufacturer, status, "condition" AS condition,
                payload, created_at AS "createdAt", updated_at AS "updatedAt"
      `,
      [
        centerId,
        name,
        (body.category ?? 'other').trim() || 'other',
        body.manufacturer?.trim() || null,
        (body.status ?? 'available').trim() || 'available',
        (body.condition ?? 'good').trim() || 'good',
      ],
    );
    return this.mapRow(rows[0]);
  }

  async patchStatus(
    gearId: string,
    status: string,
    actorId: string,
    actorRole?: string,
  ) {
    const g = await this.dataSource.query(
      `SELECT id, dive_center_id FROM center_gear_items WHERE id = $1::uuid LIMIT 1`,
      [gearId],
    );
    if (!g.length) {
      throw new NotFoundException('Gear item not found');
    }
    const centerId = String(g[0].dive_center_id);
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const rows = await this.dataSource.query(
      `
      UPDATE center_gear_items
      SET status = $2, updated_at = NOW()
      WHERE id = $1::uuid
      RETURNING id, dive_center_id AS "diveCenterId", name, category, manufacturer, status, "condition" AS condition,
                payload, created_at AS "createdAt", updated_at AS "updatedAt"
      `,
      [gearId, status.trim()],
    );
    return this.mapRow(rows[0]);
  }

  private mapRow(row: Record<string, unknown>) {
    const payload = (row.payload as Record<string, unknown>) ?? {};
    return {
      id: row.id,
      diveCenterId: row.diveCenterId,
      name: row.name,
      category: row.category,
      manufacturer: row.manufacturer,
      status: row.status,
      condition: row.condition,
      description: payload.description ?? '',
      model: payload.model ?? null,
      size: payload.size ?? null,
      sizes: payload.sizes ?? [],
      photos: payload.photos ?? [],
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    };
  }
}
