import {
  Controller,
  Get,
  Post,
  Patch,
  Put,
  Delete,
  Query,
  Param,
  Body,
  HttpCode,
  HttpStatus,
  UseGuards,
  Request,
} from '@nestjs/common';
import { CoursesService } from './courses.service';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

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

  @Post()
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.CREATED)
  async createCourse(@Request() req: any, @Body() body: any) {
    return this.coursesService.createCourse(req.user.sub, body);
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

  @Patch(':id')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async patchCourse(
    @Request() req: any,
    @Param('id') id: string,
    @Body() body: any,
  ) {
    return this.coursesService.updateCourse(req.user.sub, id, body);
  }

  @Put(':id')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async putCourse(
    @Request() req: any,
    @Param('id') id: string,
    @Body() body: any,
  ) {
    return this.coursesService.updateCourse(req.user.sub, id, body);
  }

  @Delete(':id')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.NO_CONTENT)
  async deleteCourse(@Request() req: any, @Param('id') id: string) {
    await this.coursesService.deleteCourse(req.user.sub, id);
  }
}
