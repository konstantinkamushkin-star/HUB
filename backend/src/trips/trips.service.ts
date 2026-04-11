import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

interface TripFilters {
  tripType?: string;
  country?: string;
  startDate?: string;
  endDate?: string;
  minCertificationLevel?: string;
  nitroxAvailable?: boolean;
  equipmentRentalAvailable?: boolean;
  availableSpots?: boolean;
  /** Dive center uuid — only trips organized by this center */
  organizerId?: string;
}

@Injectable()
export class TripsService {
  constructor(
    @InjectDataSource()
    private dataSource: DataSource,
  ) {}

  async getTrips(filters: TripFilters = {}): Promise<any[]> {
    let query = `
      SELECT 
        id,
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
        price_details,
        created_at,
        updated_at
      FROM trips
      WHERE 1=1
    `;

    const params: any[] = [];
    let paramIndex = 1;

    if (filters.tripType) {
      query += ` AND trip_type = $${paramIndex}`;
      params.push(filters.tripType);
      paramIndex++;
    }

    if (filters.country) {
      query += ` AND country = $${paramIndex}`;
      params.push(filters.country);
      paramIndex++;
    }

    if (filters.startDate) {
      query += ` AND start_date >= $${paramIndex}`;
      params.push(filters.startDate);
      paramIndex++;
    }

    if (filters.endDate) {
      query += ` AND end_date <= $${paramIndex}`;
      params.push(filters.endDate);
      paramIndex++;
    }

    if (filters.minCertificationLevel) {
      query += ` AND minimum_certification_level = $${paramIndex}`;
      params.push(filters.minCertificationLevel);
      paramIndex++;
    }

    if (filters.nitroxAvailable !== undefined) {
      query += ` AND nitrox_available = $${paramIndex}`;
      params.push(filters.nitroxAvailable);
      paramIndex++;
    }

    if (filters.equipmentRentalAvailable !== undefined) {
      query += ` AND equipment_rental_available = $${paramIndex}`;
      params.push(filters.equipmentRentalAvailable);
      paramIndex++;
    }

    if (filters.availableSpots) {
      query += ` AND (total_spots - booked_spots) > 0`;
    }

    if (filters.organizerId) {
      query += ` AND organizer_id = $${paramIndex}`;
      params.push(filters.organizerId);
      paramIndex++;
    }

    query += ` ORDER BY start_date ASC`;

    try {
      const results = await this.dataSource.query(query, params);

      return results.map((row) => ({
        id: row.id,
        organizerId: row.organizer_id,
        organizerType: row.organizer_type,
        tripType: row.trip_type,
        hotelId: row.hotel_id || null,
        yachtId: row.yacht_id || null,
        hotelLabel: row.hotel_label || null,
        yachtLabel: row.yacht_label || null,
        country: row.country,
        region: row.region || null,
        startDate: row.start_date,
        endDate: row.end_date,
        minimumCertificationLevel: row.minimum_certification_level || null,
        minimumDives: row.minimum_dives || null,
        description: row.description || '',
        photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
        totalSpots: row.total_spots || 0,
        bookedSpots: row.booked_spots || 0,
        participants: row.participants || [],
        availableCourses: Array.isArray(row.available_courses) ? row.available_courses : [],
        nitroxAvailable: row.nitrox_available || false,
        equipmentRentalAvailable: row.equipment_rental_available || false,
        groupLeaderId: row.group_leader_id || null,
        programDays: row.program_days || [],
        additionalExpenses: row.additional_expenses || [],
        priceDetails: row.price_details || {},
        createdAt: row.created_at,
        updatedAt: row.updated_at,
      }));
    } catch (error) {
      console.error('Database query error in getTrips:', error);
      console.error('Query:', query);
      console.error('Params:', params);
      throw error;
    }
  }

  async getTrip(id: string): Promise<any> {
    const query = `
      SELECT 
        id,
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
        price_details,
        created_at,
        updated_at
      FROM trips
      WHERE id = $1
    `;

    try {
      const results = await this.dataSource.query(query, [id]);
      if (results.length === 0) {
        throw new Error('Trip not found');
      }

      const row = results[0];
      return {
        id: row.id,
        organizerId: row.organizer_id,
        organizerType: row.organizer_type,
        tripType: row.trip_type,
        hotelId: row.hotel_id || null,
        yachtId: row.yacht_id || null,
        hotelLabel: row.hotel_label || null,
        yachtLabel: row.yacht_label || null,
        country: row.country,
        region: row.region || null,
        startDate: row.start_date,
        endDate: row.end_date,
        minimumCertificationLevel: row.minimum_certification_level || null,
        minimumDives: row.minimum_dives || null,
        description: row.description || '',
        photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
        totalSpots: row.total_spots || 0,
        bookedSpots: row.booked_spots || 0,
        participants: row.participants || [],
        availableCourses: Array.isArray(row.available_courses) ? row.available_courses : [],
        nitroxAvailable: row.nitrox_available || false,
        equipmentRentalAvailable: row.equipment_rental_available || false,
        groupLeaderId: row.group_leader_id || null,
        programDays: row.program_days || [],
        additionalExpenses: row.additional_expenses || [],
        priceDetails: row.price_details || {},
        createdAt: row.created_at,
        updatedAt: row.updated_at,
      };
    } catch (error) {
      console.error('Database query error in getTrip:', error);
      throw error;
    }
  }

  /** Diver requests one spot on a trip (MVP; no payment). */
  async joinTrip(
    userId: string,
    tripId: string,
  ): Promise<{ ok: true; bookedSpots: number; totalSpots: number }> {
    const rows = await this.dataSource.query(
      `SELECT id, total_spots, booked_spots, participants FROM trips WHERE id = $1`,
      [tripId],
    );
    if (!rows.length) {
      throw new NotFoundException('Trip not found');
    }
    const row = rows[0];
    const rawParts = row.participants;
    let participants: unknown[] = [];
    if (Array.isArray(rawParts)) {
      participants = rawParts;
    } else if (typeof rawParts === 'string') {
      try {
        participants = JSON.parse(rawParts) as unknown[];
      } catch {
        participants = [];
      }
    } else if (rawParts != null && typeof rawParts === 'object') {
      participants = [];
    }

    const already = participants.some((p: unknown) => {
      if (typeof p === 'string') return p === userId;
      if (p && typeof p === 'object' && 'userId' in p) {
        return (p as { userId?: string }).userId === userId;
      }
      return false;
    });
    if (already) {
      throw new ConflictException('Already joined this trip');
    }

    const total = Number(row.total_spots) || 0;
    const booked = Number(row.booked_spots) || 0;
    if (booked >= total) {
      throw new BadRequestException('No spots available');
    }

    const newEntry = { userId, joinedAt: new Date().toISOString() };
    const updated = await this.dataSource.query(
      `
      UPDATE trips SET
        participants = COALESCE(participants, '[]'::jsonb) || $2::jsonb,
        booked_spots = booked_spots + 1,
        updated_at = NOW()
      WHERE id = $1 AND booked_spots < total_spots
      RETURNING booked_spots, total_spots
      `,
      [tripId, JSON.stringify([newEntry])],
    );
    if (!updated?.length) {
      throw new ConflictException('Could not join trip');
    }
    const u = updated[0];
    return {
      ok: true,
      bookedSpots: Number(u.booked_spots),
      totalSpots: Number(u.total_spots),
    };
  }
}
