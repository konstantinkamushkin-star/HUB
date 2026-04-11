import {
  IsArray,
  IsIn,
  IsOptional,
  IsString,
  IsUUID,
  MinLength,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';

export class ChatAttachmentDto {
  @IsString()
  type: string;

  @IsString()
  url: string;

  @IsOptional()
  @IsString()
  thumbnailURL?: string;
}

export class SendChatMessageDto {
  @IsUUID()
  conversationId: string;

  @IsOptional()
  @IsString()
  content?: string;

  @IsOptional()
  @IsIn(['text', 'photo', 'voice', 'location', 'system'])
  messageType?: string;

  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => ChatAttachmentDto)
  attachments?: ChatAttachmentDto[];
}
