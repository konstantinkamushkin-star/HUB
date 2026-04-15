import {
  Injectable,
  ForbiddenException,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { AuthService } from '../auth/auth.service';

@Injectable()
export class CoursesService {
  constructor(
    @InjectDataSource()
    private dataSource: DataSource,
    private readonly authService: AuthService,
  ) {}

  private mapRow(row: any) {
    const rawIds = row.instructor_ids;
    const fromArray = Array.isArray(rawIds)
      ? rawIds.map((x: any) => String(x)).filter(Boolean)
      : [];
    const legacy = row.instructor_id ? String(row.instructor_id) : null;
    const instructorIds =
      fromArray.length > 0 ? fromArray : legacy ? [legacy] : [];
    const instructorId = instructorIds[0] ?? null;
    return {
      id: row.id,
      name: row.name,
      level: row.level,
      description: row.description || '',
      localizedDescription: row.localized_description || null,
      trainingSystems: Array.isArray(row.training_systems)
        ? row.training_systems
        : [],
      modules: row.modules || [],
      duration: row.duration || 0,
      prerequisites: Array.isArray(row.prerequisites) ? row.prerequisites : [],
      diveCenterId: row.dive_center_id || null,
      instructorId,
      instructorIds,
      photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    };
  }

  private normalizeInstructorIds(body: any): string[] {
    const fromArr = Array.isArray(body.instructorIds) ? body.instructorIds : [];
    const asStrings = fromArr
      .map((x: unknown) => String(x).trim())
      .filter((s) => s.length > 0);
    const uniq: string[] = Array.from(new Set(asStrings));
    if (uniq.length) {
      return uniq;
    }
    if (body.instructorId) {
      return [String(body.instructorId)];
    }
    return [];
  }

  private async assertActorCanManageCenter(
    actorUserId: string,
    diveCenterId: string,
  ): Promise<void> {
    const urows = await this.dataSource.query(
      `SELECT role FROM users WHERE id = $1 LIMIT 1`,
      [actorUserId],
    );
    const role = String(urows[0]?.role ?? '').toUpperCase();
    if (role === 'SUPER_ADMIN') {
      return;
    }
    const myCenter = await this.authService.getDiveCenterIdForUser(actorUserId);
    if (!myCenter || myCenter !== diveCenterId) {
      throw new ForbiddenException(
        'No permission to manage courses for this dive center',
      );
    }
  }

  private async assertInstructorsBelongToCenter(
    diveCenterId: string,
    instructorUserIds: string[],
  ): Promise<void> {
    if (!instructorUserIds.length) {
      return;
    }
    const rows = await this.dataSource.query(
      `SELECT instructor_ids FROM dive_centers WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [diveCenterId],
    );
    if (!rows.length) {
      throw new BadRequestException('Dive center not found');
    }
    const allowed = new Set(
      (rows[0].instructor_ids ?? []).map((x: string) => String(x)),
    );
    for (const uid of instructorUserIds) {
      if (!allowed.has(uid)) {
        throw new BadRequestException(
          `User ${uid} is not listed as an instructor for this dive center`,
        );
      }
    }
  }

  private extractModules(body: any): any[] {
    const m = body.modules ?? body.program;
    return Array.isArray(m) ? m : [];
  }

  async getCourses(diveCenterId?: string): Promise<any[]> {
    let query = `
      SELECT 
        id,
        name,
        level,
        description,
        localized_description,
        training_systems,
        modules,
        duration,
        prerequisites,
        dive_center_id,
        instructor_id,
        instructor_ids,
        photo_urls,
        created_at,
        updated_at
      FROM courses
      WHERE 1=1
    `;

    const params: any[] = [];
    let paramIndex = 1;

    if (diveCenterId) {
      query += ` AND dive_center_id = $${paramIndex}`;
      params.push(diveCenterId);
      paramIndex++;
    }

    query += ` ORDER BY created_at DESC`;

    try {
      const results = await this.dataSource.query(query, params);
      return results.map((row) => this.mapRow(row));
    } catch (error) {
      console.error('Database query error in getCourses:', error);
      console.error('Query:', query);
      console.error('Params:', params);
      throw error;
    }
  }

  async getCourse(id: string): Promise<any> {
    const query = `
      SELECT 
        id,
        name,
        level,
        description,
        localized_description,
        training_systems,
        modules,
        duration,
        prerequisites,
        dive_center_id,
        instructor_id,
        instructor_ids,
        photo_urls,
        created_at,
        updated_at
      FROM courses
      WHERE id = $1
    `;

    try {
      const results = await this.dataSource.query(query, [id]);
      if (results.length === 0) {
        throw new NotFoundException('Course not found');
      }
      return this.mapRow(results[0]);
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }
      console.error('Database query error in getCourse:', error);
      throw error;
    }
  }

  async createCourse(actorUserId: string, body: any): Promise<any> {
    const diveCenterId = body.diveCenterId ? String(body.diveCenterId) : null;
    if (!diveCenterId) {
      throw new BadRequestException('diveCenterId is required');
    }
    await this.assertActorCanManageCenter(actorUserId, diveCenterId);

    const instructorIds = this.normalizeInstructorIds(body);
    await this.assertInstructorsBelongToCenter(diveCenterId, instructorIds);
    const primaryInstructor = instructorIds[0] ?? null;

    const modules = this.extractModules(body);
    const name = String(body.name ?? '').trim();
    if (!name) {
      throw new BadRequestException('name is required');
    }
    const level = String(body.level ?? 'basic');
    const description = String(body.description ?? '');
    const trainingSystems = Array.isArray(body.trainingSystems)
      ? body.trainingSystems
      : [];
    const duration = Number(body.duration);
    if (!Number.isFinite(duration) || duration < 1) {
      throw new BadRequestException('duration must be a positive number');
    }
    const prerequisites = Array.isArray(body.prerequisites)
      ? body.prerequisites
      : [];
    const photos = Array.isArray(body.photos) ? body.photos : [];
    const localizedDescription = body.localizedDescription ?? null;

    const id =
      body.id && /^[0-9a-f-]{36}$/i.test(String(body.id))
        ? String(body.id)
        : null;

    const rows = await this.dataSource.query(
      `INSERT INTO courses (
        id,
        name, level, description, localized_description,
        training_systems, modules, duration, prerequisites,
        dive_center_id, instructor_id, instructor_ids, photo_urls
      ) VALUES (
        COALESCE($1::uuid, gen_random_uuid()),
        $2, $3, $4, $5::jsonb,
        $6::text[], $7::jsonb, $8, $9::text[],
        $10::uuid, $11::uuid, $12::uuid[], $13::text[]
      )
      RETURNING
        id, name, level, description, localized_description,
        training_systems, modules, duration, prerequisites,
        dive_center_id, instructor_id, instructor_ids, photo_urls,
        created_at, updated_at`,
      [
        id,
        name,
        level,
        description,
        localizedDescription,
        trainingSystems,
        JSON.stringify(modules),
        duration,
        prerequisites,
        diveCenterId,
        primaryInstructor,
        instructorIds,
        photos,
      ],
    );
    return this.mapRow(rows[0]);
  }

  async updateCourse(actorUserId: string, courseId: string, body: any): Promise<any> {
    const existing = await this.dataSource.query(
      `SELECT dive_center_id FROM courses WHERE id = $1 LIMIT 1`,
      [courseId],
    );
    if (!existing.length) {
      throw new NotFoundException('Course not found');
    }
    const diveCenterId = String(existing[0].dive_center_id);
    await this.assertActorCanManageCenter(actorUserId, diveCenterId);

    const instructorIds = this.normalizeInstructorIds(body);
    await this.assertInstructorsBelongToCenter(diveCenterId, instructorIds);
    const primaryInstructor = instructorIds[0] ?? null;

    const modules = this.extractModules(body);
    const name = String(body.name ?? '').trim();
    if (!name) {
      throw new BadRequestException('name is required');
    }
    const level = String(body.level ?? 'basic');
    const description = String(body.description ?? '');
    const trainingSystems = Array.isArray(body.trainingSystems)
      ? body.trainingSystems
      : [];
    const duration = Number(body.duration);
    if (!Number.isFinite(duration) || duration < 1) {
      throw new BadRequestException('duration must be a positive number');
    }
    const prerequisites = Array.isArray(body.prerequisites)
      ? body.prerequisites
      : [];
    const photos = Array.isArray(body.photos) ? body.photos : [];
    const localizedDescription = body.localizedDescription ?? null;

    const rows = await this.dataSource.query(
      `UPDATE courses SET
        name = $2,
        level = $3,
        description = $4,
        localized_description = $5::jsonb,
        training_systems = $6::text[],
        modules = $7::jsonb,
        duration = $8,
        prerequisites = $9::text[],
        instructor_id = $10::uuid,
        instructor_ids = $11::uuid[],
        photo_urls = $12::text[],
        updated_at = NOW()
      WHERE id = $1::uuid
      RETURNING
        id, name, level, description, localized_description,
        training_systems, modules, duration, prerequisites,
        dive_center_id, instructor_id, instructor_ids, photo_urls,
        created_at, updated_at`,
      [
        courseId,
        name,
        level,
        description,
        localizedDescription,
        trainingSystems,
        JSON.stringify(modules),
        duration,
        prerequisites,
        primaryInstructor,
        instructorIds,
        photos,
      ],
    );
    return this.mapRow(rows[0]);
  }

  async deleteCourse(actorUserId: string, courseId: string): Promise<void> {
    const existing = await this.dataSource.query(
      `SELECT dive_center_id FROM courses WHERE id = $1 LIMIT 1`,
      [courseId],
    );
    if (!existing.length) {
      throw new NotFoundException('Course not found');
    }
    const diveCenterId = String(existing[0].dive_center_id);
    await this.assertActorCanManageCenter(actorUserId, diveCenterId);
    await this.dataSource.query(`DELETE FROM courses WHERE id = $1::uuid`, [
      courseId,
    ]);
  }
}
