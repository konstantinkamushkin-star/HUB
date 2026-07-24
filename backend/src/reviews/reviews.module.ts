import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { ReviewsService } from './reviews.service';
import { ReviewsController } from './reviews.controller';
import { ReviewEntity } from './entities/review.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      ReviewEntity,
      User,
      DiveCenterEntity,
      DiveSiteEntity,
      ShopEntity,
    ]),
  ],
  controllers: [ReviewsController],
  providers: [ReviewsService],
})
export class ReviewsModule {}

