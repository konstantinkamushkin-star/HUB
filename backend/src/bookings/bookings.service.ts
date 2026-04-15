import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { isAdminRole } from '../auth/rbac/admin-roles';
import { CreateBookingDto } from './dto/create-booking.dto';

type BookingStatus =
  | 'pending'
  | 'quoted'
  | 'confirmed'
  | 'completed'
  | 'cancelled'
  | 'refunded';

@Injectable()
export class BookingsService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
  ) {}

  async createBooking(userId: string, dto: CreateBookingDto): Promise<any> {
    const rows = await this.dataSource.query(
      `
      INSERT INTO bookings (
        user_id,
        dive_center_id,
        service_id,
        dive_site_id,
        instructor_id,
        date,
        date_end,
        start_time,
        participants,
        gear_rental,
        payment,
        status,
        notes,
        booking_type,
        request_mode,
        session_id,
        participants_count,
        instructor_preferences,
        equipment_rental
      ) VALUES (
        $1, $2, $3, $4, $5, $6::timestamptz, $7::timestamptz, $8,
        $9::jsonb, $10::jsonb, $11::jsonb, $12, $13, $14, $15, $16,
        $17, $18::jsonb, $19::jsonb
      )
      RETURNING *
      `,
      [
        userId,
        dto.diveCenterId,
        dto.serviceId,
        dto.diveSiteId ?? null,
        dto.instructorId ?? null,
        dto.date,
        dto.dateEnd ?? null,
        dto.startTime,
        JSON.stringify(Array.isArray(dto.participants) ? dto.participants : []),
        dto.gearRental ? JSON.stringify(dto.gearRental) : null,
        JSON.stringify(dto.payment ?? {}),
        'pending',
        dto.notes ?? null,
        dto.bookingType ?? null,
        dto.requestMode ?? null,
        dto.sessionId ?? null,
        dto.participantsCount ?? null,
        dto.instructorPreferences
          ? JSON.stringify(dto.instructorPreferences)
          : null,
        dto.equipmentRental ? JSON.stringify(dto.equipmentRental) : null,
      ],
    );

    return this.mapRow(rows[0]);
  }

  async getBookingsForUser(
    actorId: string,
    actorRole: string | undefined,
    requestedUserId?: string,
  ): Promise<any[]> {
    const targetUserId = requestedUserId?.trim() || actorId;
    const canReadOtherUsers = isAdminRole(actorRole);
    if (!canReadOtherUsers && targetUserId !== actorId) {
      throw new ForbiddenException('You can only view your own bookings');
    }

    const rows = await this.dataSource.query(
      `SELECT * FROM bookings WHERE user_id = $1 ORDER BY created_at DESC`,
      [targetUserId],
    );
    return rows.map((row: any) => this.mapRow(row));
  }

  async getAdminBookings(
    actorId: string,
    actorRole: string | undefined,
    centerId?: string,
  ): Promise<any[]> {
    const centers = await this.resolveAllowedDiveCenters(actorId, actorRole);
    const hasGlobalAccess = isAdminRole(actorRole);

    if (!hasGlobalAccess && !centers.length) {
      throw new ForbiddenException('No booking access for this account');
    }

    const params: any[] = [];
    const where: string[] = [];

    if (hasGlobalAccess) {
      if (centerId?.trim()) {
        params.push(centerId.trim());
        where.push(`dive_center_id = $${params.length}`);
      }
    } else {
      if (centerId?.trim()) {
        const normalized = centerId.trim();
        if (!centers.includes(normalized)) {
          throw new ForbiddenException('No access to this dive center');
        }
        params.push(normalized);
        where.push(`dive_center_id = $${params.length}`);
      } else {
        params.push(centers);
        where.push(`dive_center_id = ANY($${params.length}::uuid[])`);
      }
    }

    const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
    const rows = await this.dataSource.query(
      `SELECT * FROM bookings ${whereSql} ORDER BY created_at DESC`,
      params,
    );
    return rows.map((row: any) => this.mapRow(row));
  }

  async getInstructorBookings(
    actorId: string,
    actorRole: string | undefined,
  ): Promise<any[]> {
    const centers = await this.resolveAllowedDiveCenters(actorId, actorRole);
    const params: any[] = [actorId];
    const where: string[] = [`instructor_id = $1`];

    if (centers.length) {
      params.push(centers);
      where.push(`dive_center_id = ANY($2::uuid[])`);
    }

    const rows = await this.dataSource.query(
      `SELECT * FROM bookings WHERE ${where.join(' OR ')} ORDER BY date ASC`,
      params,
    );
    return rows.map((row: any) => this.mapRow(row));
  }

  async updateBookingStatus(
    bookingId: string,
    nextStatus: BookingStatus,
    actorId: string,
    actorRole: string | undefined,
    finalPriceAmount?: number,
    finalPriceCurrency?: string,
    manualVerificationNote?: string,
  ): Promise<any> {
    const booking = await this.getBookingRowOrThrow(bookingId);
    await this.assertCanManageBooking(booking, actorId, actorRole);

    this.assertTransitionAllowed(booking.status, nextStatus);

    const shouldApplyManualPrice =
      finalPriceAmount !== undefined && Number.isFinite(finalPriceAmount);

    const currentPayment =
      booking.payment && typeof booking.payment === 'object'
        ? booking.payment
        : {};
    const nextPayment = shouldApplyManualPrice
      ? {
          ...currentPayment,
          amount: Number(finalPriceAmount),
          currency:
            finalPriceCurrency?.trim().toUpperCase() ||
            String(currentPayment.currency ?? 'USD'),
        }
      : currentPayment;

    const manualLines: string[] = [];
    if (shouldApplyManualPrice) {
      manualLines.push(
        `manual_verified_price=${Number(finalPriceAmount).toFixed(2)} ${
          finalPriceCurrency?.trim().toUpperCase() ||
          String(currentPayment.currency ?? 'USD')
        }`,
      );
    }
    if (manualVerificationNote?.trim()) {
      manualLines.push(`manual_note=${manualVerificationNote.trim()}`);
    }
    const existingNotes = String(booking.notes ?? '').trim();
    const mergedNotes = manualLines.length
      ? [existingNotes, ...manualLines].filter(Boolean).join('\n')
      : existingNotes || null;

    const updated = await this.dataSource.query(
      `
      UPDATE bookings
      SET status = $2, payment = $3::jsonb, notes = $4, updated_at = NOW()
      WHERE id = $1
      RETURNING *
      `,
      [bookingId, nextStatus, JSON.stringify(nextPayment), mergedNotes],
    );
    return this.mapRow(updated[0]);
  }

  async markCompletedByInstructor(
    bookingId: string,
    actorId: string,
    actorRole: string | undefined,
  ): Promise<any> {
    const booking = await this.getBookingRowOrThrow(bookingId);
    await this.assertCanAccessAsInstructor(booking, actorId, actorRole);

    if (!['confirmed', 'quoted'].includes(booking.status)) {
      throw new BadRequestException(
        `Cannot complete booking from status "${booking.status}"`,
      );
    }

    const updated = await this.dataSource.query(
      `
      UPDATE bookings
      SET status = 'completed', updated_at = NOW()
      WHERE id = $1
      RETURNING *
      `,
      [bookingId],
    );
    return this.mapRow(updated[0]);
  }

  private async getBookingRowOrThrow(bookingId: string): Promise<any> {
    const rows = await this.dataSource.query(
      `SELECT * FROM bookings WHERE id = $1 LIMIT 1`,
      [bookingId],
    );
    if (!rows.length) {
      throw new NotFoundException('Booking not found');
    }
    return rows[0];
  }

  private async assertCanManageBooking(
    bookingRow: any,
    actorId: string,
    actorRole: string | undefined,
  ) {
    if (isAdminRole(actorRole)) {
      return;
    }
    const allowedCenters = await this.resolveAllowedDiveCenters(
      actorId,
      actorRole,
    );
    if (!allowedCenters.includes(String(bookingRow.dive_center_id))) {
      throw new ForbiddenException('You cannot manage this booking');
    }
  }

  private async assertCanAccessAsInstructor(
    bookingRow: any,
    actorId: string,
    actorRole: string | undefined,
  ) {
    if (String(bookingRow.instructor_id ?? '') === actorId) {
      return;
    }
    const allowedCenters = await this.resolveAllowedDiveCenters(
      actorId,
      actorRole,
    );
    if (allowedCenters.includes(String(bookingRow.dive_center_id))) {
      return;
    }
    throw new ForbiddenException('You cannot update this booking');
  }

  private assertTransitionAllowed(
    currentStatus: BookingStatus,
    nextStatus: BookingStatus,
  ) {
    const transitions: Record<BookingStatus, BookingStatus[]> = {
      pending: ['pending', 'quoted', 'confirmed', 'cancelled'],
      quoted: ['quoted', 'confirmed', 'cancelled'],
      confirmed: ['confirmed', 'completed', 'cancelled', 'refunded'],
      completed: ['completed', 'refunded'],
      cancelled: ['cancelled'],
      refunded: ['refunded'],
    };
    const allowed = transitions[currentStatus] ?? [];
    if (!allowed.includes(nextStatus)) {
      throw new BadRequestException(
        `Cannot transition booking status from "${currentStatus}" to "${nextStatus}"`,
      );
    }
  }

  private async resolveAllowedDiveCenters(
    userId: string,
    role?: string,
  ): Promise<string[]> {
    if (isAdminRole(role)) {
      return [];
    }

    const byOwner = await this.dataSource.query(
      `SELECT id FROM dive_centers WHERE owner_id = $1 AND deleted_at IS NULL`,
      [userId],
    );
    const byInstructor = await this.dataSource.query(
      `SELECT id FROM dive_centers WHERE deleted_at IS NULL AND $1 = ANY(instructor_ids)`,
      [userId],
    );

    const ids = new Set<string>([
      ...byOwner.map((r: any) => String(r.id)),
      ...byInstructor.map((r: any) => String(r.id)),
    ]);

    if (role === 'DIVE_CENTER_ADMIN') {
      const users = await this.dataSource.query(
        `SELECT email FROM users WHERE id = $1 LIMIT 1`,
        [userId],
      );
      const email = String(users?.[0]?.email ?? '').trim().toLowerCase();
      if (email) {
        const byEmail = await this.dataSource.query(
          `SELECT id FROM dive_centers WHERE deleted_at IS NULL AND LOWER(TRIM(email)) = $1`,
          [email],
        );
        for (const row of byEmail) {
          ids.add(String(row.id));
        }
      }
    }

    return [...ids];
  }

  private mapRow(row: any) {
    return {
      id: row.id,
      userId: row.user_id,
      diveCenterId: row.dive_center_id,
      serviceId: row.service_id,
      diveSiteId: row.dive_site_id,
      instructorId: row.instructor_id,
      date: row.date,
      dateEnd: row.date_end,
      startTime: row.start_time,
      participants: row.participants ?? [],
      gearRental: row.gear_rental ?? null,
      payment: row.payment ?? {},
      status: row.status,
      notes: row.notes,
      bookingType: row.booking_type,
      requestMode: row.request_mode,
      sessionId: row.session_id,
      participantsCount: row.participants_count,
      instructorPreferences: row.instructor_preferences ?? null,
      equipmentRental: row.equipment_rental ?? null,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  }
}
