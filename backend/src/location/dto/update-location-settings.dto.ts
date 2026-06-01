import { ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional } from 'class-validator';

export class UpdateLocationSettingsDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  shareLocation?: boolean;

  @ApiPropertyOptional({
    description: 'Also sets diver_profile.lookingForBuddy when provided',
  })
  @IsOptional()
  @IsBoolean()
  discoverableNearby?: boolean;
}
