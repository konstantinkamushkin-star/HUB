import {
  Controller,
  Get,
  Query,
  Param,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { CoursesService } from './courses.service';

@Controller('courses')
export class CoursesController {
  constructor(private readonly coursesService: CoursesService) {}

  @Get()
  @HttpCode(HttpStatus.OK)
  async getCourses(@Query('diveCenterId') diveCenterId?: string) {
    try {
      const courses = await this.coursesService.getCourses(diveCenterId);
      return courses;
    } catch (error) {
      console.error('Error in getCourses endpoint:', error);
      throw error;
    }
  }

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  async getCourse(@Param('id') id: string) {
    try {
      const course = await this.coursesService.getCourse(id);
      return course;
    } catch (error) {
      console.error('Error in getCourse endpoint:', error);
      throw error;
    }
  }
}
