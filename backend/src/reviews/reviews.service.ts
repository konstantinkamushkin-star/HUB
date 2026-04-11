import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { CreateReviewDto } from './dto/create-review.dto';
import { ReviewableType } from './types/reviewable-type.enum';
import { ReviewEntity } from './entities/review.entity';

@Injectable()
export class ReviewsService {
  constructor(
    @InjectRepository(ReviewEntity)
    private readonly reviewRepository: Repository<ReviewEntity>,
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
  ) {}

  async createReview(userId: string, dto: CreateReviewDto) {
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

    const user = await this.userRepository.findOne({ where: { id: userId } });
    const userName = user ? `${user.firstName} ${user.lastName}`.trim() : '';
    const userAvatarURL = user?.avatarUrl;

    return {
      id: saved.id,
      userId: saved.userId,
      userName,
      userAvatarURL,
      reviewableType: saved.reviewableType as ReviewableType,
      reviewableId: saved.reviewableId,
      rating: saved.rating,
      text: saved.text,
      categories: saved.categories ?? null,
      language: saved.language,
      createdAt: saved.createdAt,
      updatedAt: saved.updatedAt,
    };
  }

  async listReviews(reviewableType: string, reviewableId: string) {
    const type = Object.values(ReviewableType).includes(reviewableType as ReviewableType)
      ? reviewableType
      : null;
    if (!type) {
      throw new BadRequestException('Invalid reviewable type');
    }

    const rows = await this.reviewRepository.find({
      where: { reviewableType: type, reviewableId },
      order: { createdAt: 'DESC', updatedAt: 'DESC' },
    });

    if (rows.length === 0) {
      return [];
    }

    const userIds = Array.from(new Set(rows.map((r) => r.userId)));
    const users = await this.userRepository.find({
      where: { id: In(userIds) },
    });
    const userMap = new Map(users.map((u) => [u.id, u]));

    return rows.map((r) => {
      const u = userMap.get(r.userId);
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
    });
  }
}

