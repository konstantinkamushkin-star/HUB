import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { DiveCentersService } from './dive-centers.service';

@Injectable()
export class CenterInventoryService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
    private readonly diveCenters: DiveCentersService,
  ) {}

  async listItems(centerId: string, actorId: string, actorRole?: string) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const rows = await this.dataSource.query(
      `
      SELECT id, dive_center_id AS "diveCenterId", name, category, status, "condition" AS condition,
             location, size, notes, issued_to_name AS "issuedToName", due_at AS "dueAt",
             checkout_notes AS "checkoutNotes", checkout_handed_off_by AS "checkoutHandedOffBy",
             checkout_handed_off_at AS "checkoutHandedOffAt", created_at AS "createdAt"
      FROM center_inventory_items
      WHERE dive_center_id = $1::uuid
      ORDER BY created_at DESC
      `,
      [centerId],
    );
    return rows;
  }

  async upsertItem(
    centerId: string,
    body: Record<string, unknown>,
    actorId: string,
    actorRole?: string,
  ) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const id = (body.id as string | undefined)?.trim();
    const name = String(body.name ?? '').trim();
    if (!name) {
      throw new BadRequestException('name required');
    }
    if (id) {
      const rows = await this.dataSource.query(
        `
        UPDATE center_inventory_items SET
          name = $2,
          category = COALESCE($3, category),
          status = COALESCE($4, status),
          "condition" = COALESCE($5, "condition"),
          location = $6,
          size = $7,
          notes = $8,
          issued_to_name = $9,
          due_at = $10,
          checkout_notes = $11,
          checkout_handed_off_by = $12,
          checkout_handed_off_at = $13::timestamptz
        WHERE id = $1::uuid AND dive_center_id = $14::uuid
        RETURNING id, dive_center_id AS "diveCenterId", name, category, status, "condition" AS condition,
          location, size, notes, issued_to_name AS "issuedToName", due_at AS "dueAt",
          checkout_notes AS "checkoutNotes", checkout_handed_off_by AS "checkoutHandedOffBy",
          checkout_handed_off_at AS "checkoutHandedOffAt", created_at AS "createdAt"
        `,
        [
          id,
          name,
          body.category ?? 'other',
          body.status ?? 'available',
          body.condition ?? 'good',
          body.location ?? null,
          body.size ?? null,
          body.notes ?? null,
          body.issuedToName ?? null,
          body.dueAt ?? null,
          body.checkoutNotes ?? null,
          body.checkoutHandedOffBy ?? null,
          body.checkoutHandedOffAt ?? null,
          centerId,
        ],
      );
      if (!rows.length) throw new NotFoundException('Item not found');
      return rows[0];
    }
    const rows = await this.dataSource.query(
      `
      INSERT INTO center_inventory_items (
        dive_center_id, name, category, status, "condition", location, size, notes,
        issued_to_name, due_at, checkout_notes, checkout_handed_off_by, checkout_handed_off_at
      ) VALUES (
        $1::uuid, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13::timestamptz
      )
      RETURNING id, dive_center_id AS "diveCenterId", name, category, status, "condition" AS condition,
        location, size, notes, issued_to_name AS "issuedToName", due_at AS "dueAt",
        checkout_notes AS "checkoutNotes", checkout_handed_off_by AS "checkoutHandedOffBy",
        checkout_handed_off_at AS "checkoutHandedOffAt", created_at AS "createdAt"
      `,
      [
        centerId,
        name,
        body.category ?? 'other',
        body.status ?? 'available',
        body.condition ?? 'good',
        body.location ?? null,
        body.size ?? null,
        body.notes ?? null,
        body.issuedToName ?? null,
        body.dueAt ?? null,
        body.checkoutNotes ?? null,
        body.checkoutHandedOffBy ?? null,
        body.checkoutHandedOffAt ?? null,
      ],
    );
    return rows[0];
  }

  async deleteItem(
    itemId: string,
    actorId: string,
    actorRole?: string,
  ): Promise<void> {
    const r = await this.dataSource.query(
      `SELECT dive_center_id FROM center_inventory_items WHERE id = $1::uuid`,
      [itemId],
    );
    if (!r.length) throw new NotFoundException('Item not found');
    const centerId = String(r[0].dive_center_id);
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    await this.dataSource.query(
      `DELETE FROM center_inventory_items WHERE id = $1::uuid`,
      [itemId],
    );
  }

  async listTickets(centerId: string, actorId: string, actorRole?: string) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    return this.dataSource.query(
      `
      SELECT id, dive_center_id AS "diveCenterId", item_id AS "itemId", item_name AS "itemName",
             title, status, priority, description, checklist, signed_by AS "signedBy",
             signed_at AS "signedAt", started_at AS "startedAt", completed_at AS "completedAt",
             events, created_at AS "createdAt"
      FROM center_inventory_tickets
      WHERE dive_center_id = $1::uuid
      ORDER BY created_at DESC
      `,
      [centerId],
    );
  }

  async upsertTicket(
    centerId: string,
    body: Record<string, unknown>,
    actorId: string,
    actorRole?: string,
  ) {
    await this.diveCenters.assertCanManageCenterForMobileAdmin(
      actorId,
      actorRole,
      centerId,
    );
    const id = (body.id as string | undefined)?.trim();
    const itemId = String(body.itemId ?? '').trim();
    const title = String(body.title ?? '').trim();
    const itemName = String(body.itemName ?? '').trim();
    if (!itemId || !title || !itemName) {
      throw new BadRequestException('itemId, itemName, title required');
    }
    if (id) {
      const rows = await this.dataSource.query(
        `
        UPDATE center_inventory_tickets SET
          title = $2,
          status = COALESCE($3, status),
          priority = COALESCE($4, priority),
          description = $5,
          checklist = $6::jsonb,
          signed_by = $7,
          signed_at = $8::timestamptz,
          started_at = $9::timestamptz,
          completed_at = $10::timestamptz,
          events = $11::jsonb
        WHERE id = $1::uuid AND dive_center_id = $12::uuid
        RETURNING id, item_id AS "itemId", item_name AS "itemName", title, status, priority,
          description, checklist, events, created_at AS "createdAt"
        `,
        [
          id,
          title,
          body.status,
          body.priority,
          body.description ?? null,
          JSON.stringify(body.checklist ?? []),
          body.signedBy ?? null,
          body.signedAt ?? null,
          body.startedAt ?? null,
          body.completedAt ?? null,
          JSON.stringify(body.events ?? []),
          centerId,
        ],
      );
      if (!rows.length) throw new NotFoundException('Ticket not found');
      return rows[0];
    }
    const rows = await this.dataSource.query(
      `
      INSERT INTO center_inventory_tickets (
        dive_center_id, item_id, item_name, title, status, priority, description, checklist, events
      ) VALUES ($1::uuid, $2::uuid, $3, $4, $5, $6, $7, $8::jsonb, $9::jsonb)
      RETURNING id, item_id AS "itemId", item_name AS "itemName", title, status, priority,
        description, checklist, events, created_at AS "createdAt"
      `,
      [
        centerId,
        itemId,
        itemName,
        title,
        body.status ?? 'open',
        body.priority ?? 'medium',
        body.description ?? null,
        JSON.stringify(body.checklist ?? []),
        JSON.stringify(body.events ?? []),
      ],
    );
    return rows[0];
  }
}
