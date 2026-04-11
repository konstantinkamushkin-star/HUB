import { Transform, Type } from 'class-transformer';
import { IsBoolean, IsNumber, IsOptional, IsString, Max, Min, ValidateNested } from 'class-validator';

function normalizePipeline(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  const s = String(value).trim().toLowerCase();
  return s === '' ? undefined : s;
}

export class ImageProcessParamsDto {
  @IsNumber()
  @Min(0)
  @Max(100)
  depth: number;

  @IsNumber()
  @Min(0)
  @Max(100)
  strength: number;

  @IsNumber()
  @Min(0)
  @Max(100)
  dehaze: number;

  @IsNumber()
  @Min(0)
  @Max(100)
  clarity: number;

  @IsNumber()
  @Min(-100)
  @Max(100)
  temperature: number;

  @IsBoolean()
  auto_ai: boolean;

  @Transform(({ value }) => normalizePipeline(value) ?? 'default')
  @IsString()
  pipeline: string = 'default';

  /** GPT restore (0..1); optional overrides for `pipeline: gpt`. */
  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_preserve_blues?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_detail_boost?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_warmth_bias?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_dehaze_strength?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_red_recovery_strength?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(1)
  gpt_noise_reduction?: number;
}

export class ProcessImageDto {
  @IsString()
  image_id: string;

  /**
   * Дублирует выбор пайплайна на корне тела: iOS шлёт сюда же значение, что и в params.pipeline,
   * чтобы оно не терялось при вложенной трансформации/валидации.
   */
  @IsOptional()
  @Transform(({ value }) => normalizePipeline(value))
  @IsString()
  pipeline?: string;

  @ValidateNested()
  @Type(() => ImageProcessParamsDto)
  params: ImageProcessParamsDto;
}
