import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators/public.decorator';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CreateReviewDto } from './dto/create-review.dto';
import { UpdateReviewDto } from './dto/update-review.dto';
import { ReviewsService } from './reviews.service';

@ApiTags('reviews')
@Controller('reviews')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class ReviewsController {
  constructor(private readonly reviewsService: ReviewsService) {}

  @Post()
  @ApiOperation({ summary: 'Create a review (one per user per place)' })
  async create(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateReviewDto,
  ) {
    return this.reviewsService.createReview(req.user.sub, dto);
  }

  @Patch(':id')
  @ApiOperation({ summary: 'Update own review' })
  async update(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
    @Body() dto: UpdateReviewDto,
  ) {
    return this.reviewsService.updateReview(req.user.sub, id, dto);
  }

  @Delete(':id')
  @ApiOperation({ summary: 'Delete own review' })
  async remove(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
  ) {
    return this.reviewsService.deleteReview(req.user.sub, id);
  }

  @Public()
  @Get()
  @ApiOperation({ summary: 'List reviews by reviewable type + id (public)' })
  async list(
    @Query('type') type: string,
    @Query('id') id: string,
  ) {
    return this.reviewsService.listReviews(type, id);
  }
}
