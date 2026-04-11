import { IsUrl, IsUUID } from 'class-validator';

export class ImportTripUrlDto {
  @IsUrl({ require_protocol: true })
  url: string;

  @IsUUID()
  diveCenterId: string;
}
