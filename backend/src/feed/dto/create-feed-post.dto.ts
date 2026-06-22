import { IsArray, IsIn, IsOptional, IsString, IsUUID } from 'class-validator';

export class CreateFeedPostDto {
  @IsIn(['dive', 'text', 'photo', 'video'])
  type: string;

  @IsOptional()
  @IsString()
  content?: string;

  @IsOptional()
  @IsUUID()
  diveLogId?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  photos?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  videos?: string[];
}
