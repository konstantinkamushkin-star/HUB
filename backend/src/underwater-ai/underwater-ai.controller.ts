import {
  Controller,
  Get,
  Post,
  UseInterceptors,
  UploadedFile,
  BadRequestException,
  Body,
  ServiceUnavailableException,
  StreamableFile,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { UnderwaterAiService, ProcessUnderwaterOptions } from './underwater-ai.service';

@Controller('v1/underwater-ai')
export class UnderwaterAiController {
  constructor(private readonly underwaterAi: UnderwaterAiService) {}

  @Get('ping')
  ping(): { ok: boolean } {
    return { ok: true };
  }

  @Post('health')
  async health(): Promise<{ available: boolean }> {
    return { available: this.underwaterAi.isAvailable() };
  }

  @Post('process')
  @UseInterceptors(FileInterceptor('image', { limits: { fileSize: 25 * 1024 * 1024 } }))
  async process(
    @UploadedFile() file: Express.Multer.File | undefined,
    @Body('depth_m') depthMeters?: string,
    @Body('strength') strength?: string,
    @Body('use_ai') useAi?: string,
    @Body('pipeline') pipeline?: string,
    @Body('gpt_preserve_blues') gptPreserveBlues?: string,
    @Body('gpt_detail_boost') gptDetailBoost?: string,
    @Body('gpt_warmth_bias') gptWarmthBias?: string,
    @Body('gpt_dehaze_strength') gptDehazeStrength?: string,
    @Body('gpt_red_recovery_strength') gptRedRecoveryStrength?: string,
    @Body('gpt_noise_reduction') gptNoiseReduction?: string,
  ): Promise<StreamableFile> {
    if (!file?.buffer) {
      throw new BadRequestException('Image file is required (field: image)');
    }
    if (!this.underwaterAi.isAvailable()) {
      throw new ServiceUnavailableException(
        'AI underwater service is not configured. Set AI_UNDERWATER_SERVICE_URL in backend.',
      );
    }

    const plRaw = (pipeline || 'default').trim();
    const pl = plRaw.toLowerCase() || 'default';
    const optFloat = (s: string | undefined): number | undefined => {
      if (s == null || String(s).trim() === '') return undefined;
      const x = parseFloat(String(s));
      return Number.isFinite(x) ? x : undefined;
    };
    const options: ProcessUnderwaterOptions = {
      depthMeters: depthMeters != null ? parseFloat(depthMeters) : 10,
      strength: strength != null ? parseFloat(strength) : 0.7,
      useAi: pl === 'default' ? useAi !== 'false' : false,
      pipeline: plRaw || 'default',
      gptPreserveBlues: optFloat(gptPreserveBlues),
      gptDetailBoost: optFloat(gptDetailBoost),
      gptWarmthBias: optFloat(gptWarmthBias),
      gptDehazeStrength: optFloat(gptDehazeStrength),
      gptRedRecoveryStrength: optFloat(gptRedRecoveryStrength),
      gptNoiseReduction: optFloat(gptNoiseReduction),
    };

    const result = await this.underwaterAi.processImage(file.buffer, options);
    return new StreamableFile(result, { type: 'image/jpeg' });
  }
}
