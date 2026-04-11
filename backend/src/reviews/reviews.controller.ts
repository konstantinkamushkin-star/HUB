import { Body, Controller, Get, Post, Query, Request, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CreateReviewDto } from './dto/create-review.dto';
import { ReviewsService } from './reviews.service';

@ApiTags('reviews')
@Controller('reviews')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class ReviewsController {
  constructor(private readonly reviewsService: ReviewsService) {}

  @Post()
  @ApiOperation({ summary: 'Create a review' })
  async create(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateReviewDto,
  ) {
    return this.reviewsService.createReview(req.user.sub, dto);
  }

  @Get()
  @ApiOperation({ summary: 'List reviews by reviewable type + id' })
  async list(
    @Query('type') type: string,
    @Query('id') id: string,
  ) {
    return this.reviewsService.listReviews(type, id);
  }
}

