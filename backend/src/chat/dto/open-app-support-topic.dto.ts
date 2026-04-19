import { IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class OpenAppSupportTopicDto {
  /** Omit to start a new topic (server generates topicId and returns it). */
  @IsOptional()
  @IsUUID()
  topicId?: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  title?: string;
}
