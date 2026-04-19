import { Type } from 'class-transformer';
import {
  IsIn,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
  MinLength,
  ValidateNested,
} from 'class-validator';

export class SupportTicketClientMetadataDto {
  @IsOptional()
  @IsString()
  @MaxLength(64)
  appVersion?: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  build?: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  os?: string;

  @IsOptional()
  @IsString()
  @MaxLength(32)
  locale?: string;
}

export class PublicCreateSupportTicketDto {
  @IsString()
  @MinLength(3)
  @MaxLength(512)
  subject: string;

  @IsString()
  @MinLength(1)
  @MaxLength(20000)
  body: string;

  @IsOptional()
  @IsString()
  @IsIn(['feedback', 'bug', 'other'])
  category?: string;

  @IsOptional()
  @IsUUID()
  conversationId?: string;

  @IsOptional()
  @ValidateNested()
  @Type(() => SupportTicketClientMetadataDto)
  metadata?: SupportTicketClientMetadataDto;
}
