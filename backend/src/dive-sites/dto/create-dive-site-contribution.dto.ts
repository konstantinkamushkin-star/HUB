import {
  IsEnum,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
  ValidateIf,
} from 'class-validator';

export enum CreateDiveSiteContributionTypeDto {
  correction = 'correction',
  new_site = 'new_site',
}

export class CreateDiveSiteContributionDto {
  @IsEnum(CreateDiveSiteContributionTypeDto)
  type: CreateDiveSiteContributionTypeDto;

  @ValidateIf((o) => o.type === CreateDiveSiteContributionTypeDto.correction)
  @IsUUID('4')
  diveSiteId?: string;

  @IsOptional()
  @IsObject()
  proposedData?: Record<string, unknown>;

  @IsOptional()
  @IsString()
  @MaxLength(8000)
  message?: string;
}
