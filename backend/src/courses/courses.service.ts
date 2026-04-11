import { Injectable } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

@Injectable()
export class CoursesService {
  constructor(
    @InjectDataSource()
    private dataSource: DataSource,
  ) {}

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

      return results.map((row) => ({
        id: row.id,
        name: row.name,
        level: row.level,
        description: row.description || '',
        localizedDescription: row.localized_description || null,
        trainingSystems: Array.isArray(row.training_systems) ? row.training_systems : [],
        modules: row.modules || [],
        duration: row.duration || 0,
        prerequisites: Array.isArray(row.prerequisites) ? row.prerequisites : [],
        diveCenterId: row.dive_center_id || null,
        instructorId: row.instructor_id || null,
        photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
        createdAt: row.created_at,
        updatedAt: row.updated_at,
      }));
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
        photo_urls,
        created_at,
        updated_at
      FROM courses
      WHERE id = $1
    `;

    try {
      const results = await this.dataSource.query(query, [id]);
      if (results.length === 0) {
        throw new Error('Course not found');
      }

      const row = results[0];
      return {
        id: row.id,
        name: row.name,
        level: row.level,
        description: row.description || '',
        localizedDescription: row.localized_description || null,
        trainingSystems: Array.isArray(row.training_systems) ? row.training_systems : [],
        modules: row.modules || [],
        duration: row.duration || 0,
        prerequisites: Array.isArray(row.prerequisites) ? row.prerequisites : [],
        diveCenterId: row.dive_center_id || null,
        instructorId: row.instructor_id || null,
        photos: Array.isArray(row.photo_urls) ? row.photo_urls : [],
        createdAt: row.created_at,
        updatedAt: row.updated_at,
      };
    } catch (error) {
      console.error('Database query error in getCourse:', error);
      throw error;
    }
  }
}
