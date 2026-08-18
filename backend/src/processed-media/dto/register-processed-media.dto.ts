import { IsIn, IsOptional, IsString, IsUUID, MaxLength } from 'class-validator';

export class RegisterProcessedMediaDto {
  @IsUUID()
  clientId: string;

  @IsIn(['image', 'video'])
  kind: 'image' | 'video';

  @IsIn(['offline', 'server'])
  source: 'offline' | 'server';

  @IsOptional()
  @IsString()
  @MaxLength(32)
  engine?: string;

  @IsString()
  @MaxLength(512)
  mediaPath: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  thumbnailPath?: string;
}
