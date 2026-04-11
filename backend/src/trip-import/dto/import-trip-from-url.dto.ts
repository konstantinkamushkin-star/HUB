import { IsOptional, IsUrl, IsUUID, Max, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class ImportTripFromUrlDto {
  @IsUrl({ require_protocol: true })
  url: string;

  @IsUUID()
  diveCenterId: string;

  /** Сколько кандидатов картинок отдать в LLM (по умолчанию 24). */
  @IsOptional()
  @Type(() => Number)
  @Min(4)
  @Max(60)
  maxImageCandidates?: number;

  /** Сколько фото реально скачать и залить в /api/media (по умолчанию 6). */
  @IsOptional()
  @Type(() => Number)
  @Min(0)
  @Max(12)
  maxPhotosToMirror?: number;
}
