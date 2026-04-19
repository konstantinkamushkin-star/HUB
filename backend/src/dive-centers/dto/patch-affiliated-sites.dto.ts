import { IsArray, IsUUID } from 'class-validator';

export class PatchAffiliatedSitesDto {
  @IsArray()
  @IsUUID('4', { each: true })
  siteIds: string[];
}
