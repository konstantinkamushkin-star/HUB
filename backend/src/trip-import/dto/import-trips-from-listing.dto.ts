import { Type } from 'class-transformer';
import { IsOptional, IsUrl, IsUUID, Max, Min } from 'class-validator';

export class ImportTripsFromListingDto {
  @IsUrl({ require_protocol: true })
  listingUrl: string;

  @IsUUID()
  diveCenterId: string;

  @IsOptional()
  @Type(() => Number)
  @Min(1)
  @Max(25)
  maxTrips?: number;

  @IsOptional()
  @Type(() => Number)
  @Min(4)
  @Max(80)
  maxListingLinks?: number;
}
