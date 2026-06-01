import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional } from 'class-validator';

export class UpdatePrivacySettingsDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  shareLocation?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  publicProfile?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  showInFriendSearch?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  shareLogbook?: boolean;
}
