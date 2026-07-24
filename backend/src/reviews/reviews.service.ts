import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { CreateReviewDto } from './dto/create-review.dto';
import { UpdateReviewDto } from './dto/update-review.dto';
import { ReviewableType } from './types/reviewable-type.enum';
import { ReviewEntity } from './entities/review.entity';

@Injectable()
export class ReviewsService {
  constructor(
    @InjectRepository(ReviewEntity)
    private readonly reviewRepository: Repository<ReviewEntity>,
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private readonly diveCenterRepository: Repository<DiveCenterEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly diveSiteRepository: Repository<DiveSiteEntity>,
    @InjectRepository(ShopEntity)
    private readonly shopRepository: Repository<ShopEntity>,
  ) {}

  async createReview(userId: string, dto: CreateReviewDto) {
    const existing = await this.reviewRepository.findOne({
      where: {
        userId,
        reviewableType: dto.reviewableType,
        reviewableId: dto.reviewableId,
      },
    });
    if (existing) {
      throw new ConflictException(
        'You already reviewed this place. Edit or delete your existing review.',
      );
    }

    const language = dto.language?.trim() ? dto.language.trim() : 'en';
    const saved = await this.reviewRepository.save(
      this.reviewRepository.create({
        userId,
        reviewableType: dto.reviewableType,
        reviewableId: dto.reviewableId,
        rating: dto.rating,
        text: dto.text.trim(),
        language,
        categories: null,
      }),
    );

    await this.recalculateAggregates(dto.reviewableType, dto.reviewableId);
    return this.toResponse(saved);
  }

  async updateReview(userId: string, reviewId: string, dto: UpdateReviewDto) {
    const review = await this.reviewRepository.findOne({ where: { id: reviewId } });
    if (!review) {
      throw new NotFoundException('Review not found');
    }
    if (review.userId !== userId) {
      throw new ForbiddenException('You can only edit your own review');
    }
    if (dto.rating == null && dto.text == null && dto.language == null) {
      throw new BadRequestException('Nothing to update');
    }
    if (dto.rating != null) {
      review.rating = dto.rating;
    }
    if (dto.text != null) {
      review.text = dto.text.trim();
    }
    if (dto.language?.trim()) {
      review.language = dto.language.trim();
    }
    const saved = await this.reviewRepository.save(review);
    await this.recalculateAggregates(saved.reviewableType, saved.reviewableId);
    return this.toResponse(saved);
  }

  async deleteReview(userId: string, reviewId: string) {
    const review = await this.reviewRepository.findOne({ where: { id: reviewId } });
    if (!review) {
      throw new NotFoundException('Review not found');
    }
    if (review.userId !== userId) {
      throw new ForbiddenException('You can only delete your own review');
    }
    const { reviewableType, reviewableId } = review;
    await this.reviewRepository.delete({ id: reviewId });
    await this.recalculateAggregates(reviewableType, reviewableId);
    return { success: true };
  }

  async listReviews(reviewableType: string, reviewableId: string) {
    const type = Object.values(ReviewableType).includes(reviewableType as ReviewableType)
      ? (reviewableType as ReviewableType)
      : null;
    if (!type) {
      throw new BadRequestException('Invalid reviewable type');
    }

    const rows = await this.reviewRepository.find({
      where: { reviewableType: type, reviewableId },
      order: { createdAt: 'DESC', updatedAt: 'DESC' },
    });

    // Self-heal stale denormalized counters (legacy reviews created before aggregates).
    await this.recalculateAggregates(type, reviewableId, rows);

    if (rows.length === 0) {
      return [];
    }

    const userIds = Array.from(new Set(rows.map((r) => r.userId)));
    const users = await this.userRepository.find({
      where: { id: In(userIds) },
    });
    const userMap = new Map(users.map((u) => [u.id, u]));

    return rows.map((r) => this.mapRow(r, userMap.get(r.userId)));
  }

  private async toResponse(saved: ReviewEntity) {
    const user = await this.userRepository.findOne({ where: { id: saved.userId } });
    return this.mapRow(saved, user);
  }

  private mapRow(r: ReviewEntity, u?: User | null) {
    const userName = u ? `${u.firstName} ${u.lastName}`.trim() : '';
    return {
      id: r.id,
      userId: r.userId,
      userName,
      userAvatarURL: u?.avatarUrl ?? null,
      reviewableType: r.reviewableType as ReviewableType,
      reviewableId: r.reviewableId,
      rating: r.rating,
      text: r.text,
      categories: r.categories ?? null,
      language: r.language,
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    };
  }

  /**
   * Keep dive_sites / dive_centers / shops.average_rating + review_count in sync
   * with the reviews table.
   */
  private async recalculateAggregates(
    reviewableType: ReviewableType | string,
    reviewableId: string,
    knownRows?: ReviewEntity[],
  ) {
    const rows =
      knownRows ??
      (await this.reviewRepository.find({
        where: {
          reviewableType: reviewableType as ReviewableType,
          reviewableId,
        },
        select: ['rating'],
      }));

    const reviewCount = rows.length;
    const averageRating =
      reviewCount === 0
        ? 0
        : Math.round(
            (rows.reduce((sum, r) => sum + Number(r.rating || 0), 0) / reviewCount) * 100,
          ) / 100;

    const needsUpdate = async (
      current: { average_rating?: unknown; review_count?: unknown } | null,
    ) => {
      if (!current) return false;
      const curCount = Number(current.review_count || 0);
      const curAvg = Math.round(Number(current.average_rating || 0) * 100) / 100;
      return curCount !== reviewCount || curAvg !== averageRating;
    };

    switch (reviewableType) {
      case ReviewableType.dive_center: {
        const current = await this.diveCenterRepository.findOne({
          where: { id: reviewableId },
          select: ['id', 'average_rating', 'review_count'],
        });
        if (await needsUpdate(current)) {
          await this.diveCenterRepository.update(
            { id: reviewableId },
            { average_rating: averageRating, review_count: reviewCount },
          );
        }
        break;
      }
      case ReviewableType.dive_site: {
        const current = await this.diveSiteRepository.findOne({
          where: { id: reviewableId },
          select: ['id', 'average_rating', 'review_count'],
        });
        if (await needsUpdate(current)) {
          await this.diveSiteRepository.update(
            { id: reviewableId },
            { average_rating: averageRating, review_count: reviewCount },
          );
        }
        break;
      }
      case ReviewableType.shop: {
        const current = await this.shopRepository.findOne({
          where: { id: reviewableId },
          select: ['id', 'average_rating', 'review_count'],
        });
        if (await needsUpdate(current)) {
          await this.shopRepository.update(
            { id: reviewableId },
            { average_rating: averageRating, review_count: reviewCount },
          );
        }
        break;
      }
      default:
        break;
    }
  }
}
