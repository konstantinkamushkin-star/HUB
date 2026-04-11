import { IsIn, IsInt, IsNotEmpty, IsOptional, IsString, IsUUID, Max, Min } from 'class-validator';
import { ReviewableType } from '../types/reviewable-type.enum';

export class CreateReviewDto {
  @IsIn(Object.values(ReviewableType))
  reviewableType: ReviewableType;

  @IsUUID()
  reviewableId: string;

  @IsInt()
  @Min(1)
  @Max(5)
  rating: number;

  @IsString()
  @IsNotEmpty()
  text: string;

  @IsOptional()
  @IsString()
  language?: string;
}

