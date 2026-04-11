import { IsIn, IsObject, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateNotificationCampaignDto {
  @IsString()
  @IsIn(['push', 'email', 'in_app', 'sms'])
  channel: string;

  @IsString()
  @MinLength(2)
  @MaxLength(255)
  title: string;

  @IsString()
  @MinLength(2)
  @MaxLength(5000)
  body: string;

  @IsOptional()
  @IsObject()
  audience?: Record<string, unknown>;

  @IsString()
  @MinLength(3)
  @MaxLength(500)
  reason: string;
}
