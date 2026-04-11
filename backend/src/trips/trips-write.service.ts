import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { UpdateTripDto } from './dto/update-trip.dto';

export type CreateTripRowInput = {
  organizerId: string;
  organizerType: 'dive_center' | 'user';
  tripType: 'daily' | 'safari';
  hotelId: string | null;
  yachtId: string | null;
  hotelLabel: string | null;
  yachtLabel: string | null;
  country: string;
  region: string | null;
  startDate: string;
  endDate: string;
  minimumCertificationLevel: string | null;
  minimumDives: number | null;
  description: string;
  photoUrls: string[];
  totalSpots: number;
  nitroxAvailable: boolean;
  equipmentRentalAvailable: boolean;
  groupLeaderId: string | null;
  programDays: unknown[];
  additionalExpenses: unknown[];
  priceDetails: Record<string, unknown>;
  availableCourseIds: string[];
};

/**
 * Прямая запись в таблицу trips (публичный API создания в Nest пока не подключён).
 */
@Injectable()
export class TripsWriteService {
  constructor(
    @InjectDataSource()
    private readonly ds: DataSource,
  ) {}

  async createTrip(input: CreateTripRowInput): Promise<{ id: string }> {
    const q = `
      INSERT INTO trips (
        organizer_id,
        organizer_type,
        trip_type,
        hotel_id,
        yacht_id,
        hotel_label,
        yacht_label,
        country,
        region,
        start_date,
        end_date,
        minimum_certification_level,
        minimum_dives,
        description,
        photo_urls,
        total_spots,
        booked_spots,
        participants,
        available_courses,
        nitrox_available,
        equipment_rental_available,
        group_leader_id,
        program_days,
        additional_expenses,
        price_details
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7, $8, $9, $10::date, $11::date,
        $12, $13, $14, $15::text[], $16, 0, '[]'::jsonb, $17::uuid[],
        $18, $19, $20, $21::jsonb, $22::jsonb, $23::jsonb
      )
      RETURNING id
    `;
    const rows = await this.ds.query(q, [
      input.organizerId,
      input.organizerType,
      input.tripType,
      input.hotelId,
      input.yachtId,
      input.hotelLabel,
      input.yachtLabel,
      input.country,
      input.region,
      input.startDate,
      input.endDate,
      input.minimumCertificationLevel,
      input.minimumDives,
      input.description,
      input.photoUrls,
      input.totalSpots,
      input.availableCourseIds?.length ? input.availableCourseIds : [],
      input.nitroxAvailable,
      input.equipmentRentalAvailable,
      input.groupLeaderId,
      JSON.stringify(input.programDays ?? []),
      JSON.stringify(input.additionalExpenses ?? []),
      JSON.stringify(input.priceDetails ?? { currency: 'USD' }),
    ]);
    return { id: rows[0]?.id };
  }

  async assertDiveCenterExists(id: string): Promise<void> {
    const r = await this.ds.query(
      `SELECT 1 FROM dive_centers WHERE id = $1 LIMIT 1`,
      [id],
    );
    if (!r?.length) {
      throw new NotFoundException(`Dive center not found: ${id}`);
    }
  }

  /**
   * Те же правила, что и AuthService.resolveDiveCenterIdForUser: владелец, инструктор в центре,
   * или DIVE_CENTER_ADMIN с email центра = email пользователя.
   */
  async assertUserCanImportTripsForDiveCenter(
    diveCenterId: string,
    userId: string,
    userRole?: string,
  ): Promise<void> {
    const roleUpper = (userRole ?? '').toUpperCase();
    if (roleUpper === 'SUPER_ADMIN') {
      await this.assertDiveCenterExists(diveCenterId);
      return;
    }
    const r = await this.ds.query(
      `SELECT id, owner_id, email, instructor_ids FROM dive_centers WHERE id = $1 LIMIT 1`,
      [diveCenterId],
    );
    if (!r?.length) {
      throw new NotFoundException(`Dive center not found: ${diveCenterId}`);
    }
    const row = r[0] as {
      owner_id?: string | null;
      email?: string | null;
      instructor_ids?: string[] | null;
    };
    if (row.owner_id === userId) {
      return;
    }
    const instructors = Array.isArray(row.instructor_ids)
      ? row.instructor_ids
      : [];
    if (instructors.includes(userId)) {
      return;
    }
    const role = (userRole ?? '').toUpperCase();
    if (role === 'DIVE_CENTER_ADMIN') {
      const uRows = await this.ds.query(`SELECT email FROM users WHERE id = $1 LIMIT 1`, [
        userId,
      ]);
      const uEmail = String(uRows[0]?.email ?? '')
        .toLowerCase()
        .trim();
      const cEmail = String(row.email ?? '')
        .toLowerCase()
        .trim();
      if (uEmail && cEmail && uEmail === cEmail) {
        return;
      }
    }
    throw new ForbiddenException(
      'You can import trips only for a dive center you manage',
    );
  }

  /**
   * Organizer is either a dive center (managed by owner/instructor/admin-email match)
   * or a user row (organizer_id === userId).
   */
  async assertUserCanManageTrip(
    tripId: string,
    userId: string,
    userRole?: string,
  ): Promise<{
    organizerId: string;
    organizerType: string;
    bookedSpots: number;
  }> {
    const r = await this.ds.query(
      `SELECT organizer_id, organizer_type, booked_spots FROM trips WHERE id = $1 LIMIT 1`,
      [tripId],
    );
    if (!r?.length) {
      throw new NotFoundException('Trip not found');
    }
    const row = r[0] as {
      organizer_id: string;
      organizer_type: string;
      booked_spots: string | number;
    };
    const organizerId = String(row.organizer_id ?? '');
    const organizerType = String(row.organizer_type ?? '').toLowerCase();
    const bookedSpots = Number(row.booked_spots) || 0;

    if (organizerType === 'user') {
      if (organizerId === userId) {
        return { organizerId, organizerType, bookedSpots };
      }
      throw new ForbiddenException('You cannot modify this trip');
    }

    if (organizerType === 'dive_center') {
      await this.assertUserCanImportTripsForDiveCenter(
        organizerId,
        userId,
        userRole,
      );
      return { organizerId, organizerType, bookedSpots };
    }

    throw new ForbiddenException('You cannot modify this trip');
  }

  async updateTripById(
    tripId: string,
    dto: UpdateTripDto,
    userId: string,
    userRole?: string,
  ): Promise<{ id: string }> {
    if (dto.endDate < dto.startDate) {
      throw new BadRequestException('endDate must be on or after startDate');
    }
    const { bookedSpots } = await this.assertUserCanManageTrip(
      tripId,
      userId,
      userRole,
    );
    if (dto.totalSpots < bookedSpots) {
      throw new BadRequestException(
        `totalSpots must be at least bookedSpots (${bookedSpots})`,
      );
    }

    const rows = await this.ds.query(`SELECT * FROM trips WHERE id = $1 LIMIT 1`, [
      tripId,
    ]);
    if (!rows?.length) {
      throw new NotFoundException('Trip not found');
    }
    const row = rows[0] as Record<string, unknown>;

    const tripType = dto.tripType;
    let hotelId =
      dto.hotelId === undefined
        ? (row.hotel_id as string | null)
        : dto.hotelId;
    let yachtId =
      dto.yachtId === undefined
        ? (row.yacht_id as string | null)
        : dto.yachtId;
    let hotelLabel =
      dto.hotelLabel === undefined
        ? ((row.hotel_label as string | null) ?? null)
        : dto.hotelLabel?.toString().trim() || null;
    let yachtLabel =
      dto.yachtLabel === undefined
        ? ((row.yacht_label as string | null) ?? null)
        : dto.yachtLabel?.toString().trim() || null;
    if (tripType === 'daily') {
      yachtId = null;
      yachtLabel = null;
    } else {
      hotelId = null;
      hotelLabel = null;
    }

    const region =
      dto.region === undefined
        ? ((row.region as string | null) ?? null)
        : dto.region?.trim() || null;
    const minimumCertificationLevel =
      dto.minimumCertificationLevel === undefined
        ? ((row.minimum_certification_level as string | null) ?? null)
        : dto.minimumCertificationLevel?.trim() || null;
    const minimumDives =
      dto.minimumDives === undefined
        ? row.minimum_dives != null
          ? Number(row.minimum_dives)
          : null
        : dto.minimumDives;
    const nitroxAvailable =
      dto.nitroxAvailable === undefined || dto.nitroxAvailable === null
        ? Boolean(row.nitrox_available)
        : dto.nitroxAvailable;
    const equipmentRentalAvailable =
      dto.equipmentRentalAvailable === undefined ||
      dto.equipmentRentalAvailable === null
        ? Boolean(row.equipment_rental_available)
        : dto.equipmentRentalAvailable;
    const groupLeaderId =
      dto.groupLeaderId === undefined
        ? ((row.group_leader_id as string | null) ?? null)
        : dto.groupLeaderId;

    let programDays: unknown = row.program_days ?? [];
    if (!Array.isArray(programDays)) {
      programDays = [];
    }
    if (dto.programDays !== undefined) {
      programDays = dto.programDays;
    }
    let additionalExpenses: unknown = row.additional_expenses ?? [];
    if (!Array.isArray(additionalExpenses)) {
      additionalExpenses = [];
    }
    if (dto.additionalExpenses !== undefined) {
      additionalExpenses = dto.additionalExpenses;
    }
    let priceDetails: Record<string, unknown> =
      (row.price_details as Record<string, unknown>) || { currency: 'USD' };
    if (dto.priceDetails !== undefined && dto.priceDetails !== null) {
      priceDetails = dto.priceDetails as Record<string, unknown>;
    }
    let photoUrls: string[] = Array.isArray(row.photo_urls)
      ? (row.photo_urls as string[])
      : [];
    if (dto.photoUrls !== undefined) {
      photoUrls = dto.photoUrls;
    }
    let availableCourseIds: string[] = Array.isArray(row.available_courses)
      ? (row.available_courses as string[])
      : [];
    if (dto.availableCourseIds !== undefined) {
      availableCourseIds = dto.availableCourseIds;
    }

    await this.ds.query(
      `
      UPDATE trips SET
        trip_type = $2,
        hotel_id = $3,
        yacht_id = $4,
        hotel_label = $5,
        yacht_label = $6,
        country = $7,
        region = $8,
        start_date = $9::date,
        end_date = $10::date,
        minimum_certification_level = $11,
        minimum_dives = $12,
        description = $13,
        photo_urls = $14::text[],
        total_spots = $15,
        available_courses = $16::uuid[],
        nitrox_available = $17,
        equipment_rental_available = $18,
        group_leader_id = $19,
        program_days = $20::jsonb,
        additional_expenses = $21::jsonb,
        price_details = $22::jsonb,
        updated_at = NOW()
      WHERE id = $1
      `,
      [
        tripId,
        tripType,
        hotelId,
        yachtId,
        hotelLabel,
        yachtLabel,
        dto.country.trim(),
        region,
        dto.startDate,
        dto.endDate,
        minimumCertificationLevel,
        minimumDives,
        dto.description.trim(),
        photoUrls,
        dto.totalSpots,
        availableCourseIds,
        nitroxAvailable,
        equipmentRentalAvailable,
        groupLeaderId,
        JSON.stringify(Array.isArray(programDays) ? programDays : []),
        JSON.stringify(
          Array.isArray(additionalExpenses) ? additionalExpenses : [],
        ),
        JSON.stringify(priceDetails ?? { currency: 'USD' }),
      ],
    );

    return { id: tripId };
  }

  async deleteTripById(
    tripId: string,
    userId: string,
    userRole?: string,
  ): Promise<void> {
    const { bookedSpots } = await this.assertUserCanManageTrip(
      tripId,
      userId,
      userRole,
    );
    if (bookedSpots > 0) {
      throw new BadRequestException(
        'Cannot delete a trip that already has bookings',
      );
    }
    const del = await this.ds.query(
      `DELETE FROM trips WHERE id = $1 RETURNING id`,
      [tripId],
    );
    if (!del?.length) {
      throw new NotFoundException('Trip not found');
    }
  }
}
