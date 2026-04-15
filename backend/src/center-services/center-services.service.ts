import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { isAdminRole } from '../auth/rbac/admin-roles';
import { CreateCenterServiceDto } from './dto/create-center-service.dto';
import { UpdateCenterServiceDto } from './dto/update-center-service.dto';

@Injectable()
export class CenterServicesService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
  ) {}

  async listByCenter(
    diveCenterId: string,
    includeInactive = false,
  ): Promise<any[]> {
    const params: any[] = [diveCenterId];
    const where: string[] = ['dive_center_id = $1'];
    if (!includeInactive) {
      where.push('is_active = true');
    }

    const rows = await this.dataSource.query(
      `
      SELECT *
      FROM center_services
      WHERE ${where.join(' AND ')}
      ORDER BY created_at DESC
      `,
      params,
    );

    return rows.map((row: any) => this.mapRow(row));
  }

  async create(
    actorUserId: string,
    actorRole: string | undefined,
    dto: CreateCenterServiceDto,
  ): Promise<any> {
    await this.assertCanManageCenter(actorUserId, actorRole, dto.diveCenterId);

    const rows = await this.dataSource.query(
      `
      INSERT INTO center_services (
        dive_center_id,
        name,
        description,
        service_type,
        base_price_amount,
        currency,
        pricing_unit,
        duration_minutes,
        max_participants,
        requirements,
        included_items,
        pricing_rules,
        own_gear_discount_percent,
        group_discount_threshold,
        group_discount_percent,
        night_dive_surcharge_amount,
        private_instructor_surcharge_amount,
        is_active
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7, $8, $9,
        $10::text[], $11::text[], $12::jsonb,
        $13, $14, $15, $16, $17, $18
      )
      RETURNING *
      `,
      [
        dto.diveCenterId,
        dto.name.trim(),
        dto.description?.trim() ?? '',
        (dto.serviceType ?? 'fun_dive').trim().toLowerCase(),
        dto.basePriceAmount,
        (dto.currency ?? 'USD').trim().toUpperCase(),
        (dto.pricingUnit ?? 'per_person').trim().toLowerCase(),
        dto.durationMinutes ?? 0,
        dto.maxParticipants ?? 0,
        dto.requirements ?? [],
        dto.includedItems ?? [],
        dto.pricingRules ? JSON.stringify(dto.pricingRules) : null,
        dto.ownGearDiscountPercent ?? null,
        dto.groupDiscountThreshold ?? null,
        dto.groupDiscountPercent ?? null,
        dto.nightDiveSurchargeAmount ?? null,
        dto.privateInstructorSurchargeAmount ?? null,
        dto.isActive ?? true,
      ],
    );

    return this.mapRow(rows[0]);
  }

  async update(
    actorUserId: string,
    actorRole: string | undefined,
    serviceId: string,
    dto: UpdateCenterServiceDto,
  ): Promise<any> {
    const existing = await this.getServiceRowOrThrow(serviceId);
    await this.assertCanManageCenter(
      actorUserId,
      actorRole,
      String(existing.dive_center_id),
    );

    const payload = {
      name: dto.name?.trim() ?? existing.name,
      description: dto.description?.trim() ?? existing.description ?? '',
      serviceType: (dto.serviceType ?? existing.service_type ?? 'fun_dive')
        .trim()
        .toLowerCase(),
      basePriceAmount:
        dto.basePriceAmount !== undefined
          ? dto.basePriceAmount
          : Number(existing.base_price_amount ?? 0),
      currency: (dto.currency ?? existing.currency ?? 'USD').trim().toUpperCase(),
      pricingUnit: (dto.pricingUnit ?? existing.pricing_unit ?? 'per_person')
        .trim()
        .toLowerCase(),
      durationMinutes:
        dto.durationMinutes !== undefined
          ? dto.durationMinutes
          : Number(existing.duration_minutes ?? 0),
      maxParticipants:
        dto.maxParticipants !== undefined
          ? dto.maxParticipants
          : Number(existing.max_participants ?? 0),
      requirements:
        dto.requirements !== undefined
          ? dto.requirements
          : (Array.isArray(existing.requirements) ? existing.requirements : []),
      includedItems:
        dto.includedItems !== undefined
          ? dto.includedItems
          : (Array.isArray(existing.included_items) ? existing.included_items : []),
      pricingRules:
        dto.pricingRules !== undefined ? dto.pricingRules : existing.pricing_rules,
      ownGearDiscountPercent:
        dto.ownGearDiscountPercent !== undefined
          ? dto.ownGearDiscountPercent
          : existing.own_gear_discount_percent,
      groupDiscountThreshold:
        dto.groupDiscountThreshold !== undefined
          ? dto.groupDiscountThreshold
          : existing.group_discount_threshold,
      groupDiscountPercent:
        dto.groupDiscountPercent !== undefined
          ? dto.groupDiscountPercent
          : existing.group_discount_percent,
      nightDiveSurchargeAmount:
        dto.nightDiveSurchargeAmount !== undefined
          ? dto.nightDiveSurchargeAmount
          : existing.night_dive_surcharge_amount,
      privateInstructorSurchargeAmount:
        dto.privateInstructorSurchargeAmount !== undefined
          ? dto.privateInstructorSurchargeAmount
          : existing.private_instructor_surcharge_amount,
      isActive:
        dto.isActive !== undefined ? dto.isActive : Boolean(existing.is_active),
    };

    const rows = await this.dataSource.query(
      `
      UPDATE center_services
      SET
        name = $2,
        description = $3,
        service_type = $4,
        base_price_amount = $5,
        currency = $6,
        pricing_unit = $7,
        duration_minutes = $8,
        max_participants = $9,
        requirements = $10::text[],
        included_items = $11::text[],
        pricing_rules = $12::jsonb,
        own_gear_discount_percent = $13,
        group_discount_threshold = $14,
        group_discount_percent = $15,
        night_dive_surcharge_amount = $16,
        private_instructor_surcharge_amount = $17,
        is_active = $18,
        updated_at = NOW()
      WHERE id = $1
      RETURNING *
      `,
      [
        serviceId,
        payload.name,
        payload.description,
        payload.serviceType,
        payload.basePriceAmount,
        payload.currency,
        payload.pricingUnit,
        payload.durationMinutes,
        payload.maxParticipants,
        payload.requirements,
        payload.includedItems,
        payload.pricingRules ? JSON.stringify(payload.pricingRules) : null,
        payload.ownGearDiscountPercent ?? null,
        payload.groupDiscountThreshold ?? null,
        payload.groupDiscountPercent ?? null,
        payload.nightDiveSurchargeAmount ?? null,
        payload.privateInstructorSurchargeAmount ?? null,
        payload.isActive,
      ],
    );

    return this.mapRow(rows[0]);
  }

  async remove(
    actorUserId: string,
    actorRole: string | undefined,
    serviceId: string,
  ): Promise<void> {
    const existing = await this.getServiceRowOrThrow(serviceId);
    await this.assertCanManageCenter(
      actorUserId,
      actorRole,
      String(existing.dive_center_id),
    );
    await this.dataSource.query(`DELETE FROM center_services WHERE id = $1`, [
      serviceId,
    ]);
  }

  private async getServiceRowOrThrow(serviceId: string): Promise<any> {
    const rows = await this.dataSource.query(
      `SELECT * FROM center_services WHERE id = $1 LIMIT 1`,
      [serviceId],
    );
    if (!rows.length) {
      throw new NotFoundException('Service not found');
    }
    return rows[0];
  }

  private async assertCanManageCenter(
    actorUserId: string,
    actorRole: string | undefined,
    diveCenterId: string,
  ): Promise<void> {
    if (isAdminRole(actorRole)) {
      return;
    }
    const rows = await this.dataSource.query(
      `
      SELECT dc.id
      FROM dive_centers dc
      WHERE dc.deleted_at IS NULL
        AND (
          dc.owner_id = $1
          OR $1 = ANY(dc.instructor_ids)
        )
      LIMIT 1
      `,
      [actorUserId],
    );
    const resolved = rows.length ? String(rows[0].id) : null;
    if (!resolved || resolved !== diveCenterId) {
      throw new ForbiddenException(
        'No permission to manage services for this dive center',
      );
    }

    const exists = await this.dataSource.query(
      `SELECT 1 FROM dive_centers WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [diveCenterId],
    );
    if (!exists.length) {
      throw new BadRequestException('Dive center not found');
    }
  }

  private mapRow(row: any) {
    return {
      id: row.id,
      diveCenterId: row.dive_center_id,
      name: row.name,
      description: row.description ?? '',
      type: row.service_type ?? 'fun_dive',
      pricingUnit: row.pricing_unit ?? 'per_person',
      duration: Number(row.duration_minutes ?? 0),
      maxParticipants: Number(row.max_participants ?? 0),
      requirements: Array.isArray(row.requirements) ? row.requirements : [],
      includedItems: Array.isArray(row.included_items) ? row.included_items : [],
      pricingRules: row.pricing_rules ?? null,
      ownGearDiscountPercent:
        row.own_gear_discount_percent !== null
          ? Number(row.own_gear_discount_percent)
          : null,
      groupDiscountThreshold:
        row.group_discount_threshold !== null
          ? Number(row.group_discount_threshold)
          : null,
      groupDiscountPercent:
        row.group_discount_percent !== null
          ? Number(row.group_discount_percent)
          : null,
      nightDiveSurchargeAmount:
        row.night_dive_surcharge_amount !== null
          ? Number(row.night_dive_surcharge_amount)
          : null,
      privateInstructorSurchargeAmount:
        row.private_instructor_surcharge_amount !== null
          ? Number(row.private_instructor_surcharge_amount)
          : null,
      isActive: row.is_active === true,
      price: {
        amount: Number(row.base_price_amount ?? 0),
        currency: row.currency ?? 'USD',
      },
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  }
}
