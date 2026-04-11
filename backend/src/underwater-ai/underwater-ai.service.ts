import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosInstance } from 'axios';
import FormData from 'form-data';

export interface ProcessUnderwaterOptions {
  depthMeters?: number;
  strength?: number;
  useAi?: boolean;
  /** Python ai-service: `default` (Lee+Symmetry classical + optional ONNX) or `jmse1820` (Li et al. JMSE 2025). */
  pipeline?: string;
  /** Optional GPT multi-stage restore (0..1); forwarded only when set. */
  gptPreserveBlues?: number;
  gptDetailBoost?: number;
  gptWarmthBias?: number;
  gptDehazeStrength?: number;
  gptRedRecoveryStrength?: number;
  gptNoiseReduction?: number;
}

@Injectable()
export class UnderwaterAiService {
  private readonly logger = new Logger(UnderwaterAiService.name);
  private readonly client: AxiosInstance;
  private readonly baseUrl: string | null;

  constructor(private readonly configService: ConfigService) {
    this.baseUrl = this.configService.get<string>('AI_UNDERWATER_SERVICE_URL') || null;
    const rawTimeout = this.configService.get<string>('AI_UNDERWATER_HTTP_TIMEOUT_MS');
    const parsed = rawTimeout != null ? parseInt(String(rawTimeout).trim(), 10) : NaN;
    // JMSE (Li 2025) на 2K может занимать 60–120+ с; GPT/Article3 тоже тяжёлые на больших кадрах.
    const timeoutMs =
      Number.isFinite(parsed) && parsed >= 10000 ? parsed : 300_000;
    this.client = axios.create({
      timeout: timeoutMs,
      maxContentLength: 50 * 1024 * 1024,
      maxBodyLength: 50 * 1024 * 1024,
    });
  }

  isAvailable(): boolean {
    return !!this.baseUrl;
  }

  async processImage(
    imageBuffer: Buffer,
    options: ProcessUnderwaterOptions = {},
  ): Promise<Buffer> {
    if (!this.baseUrl) {
      throw new Error('AI underwater service is not configured. Set AI_UNDERWATER_SERVICE_URL.');
    }

    const form = new FormData();
    form.append('image', imageBuffer, {
      filename: 'image.jpg',
      contentType: 'image/jpeg',
    });
    form.append('depth_m', String(options.depthMeters ?? 10));
    form.append('strength', String(options.strength ?? 0.7));
    form.append('use_ai', options.useAi !== false ? 'true' : 'false');
    const pipeline = (options.pipeline || 'default').trim() || 'default';
    form.append('pipeline', pipeline);

    const appendOpt = (name: string, v: number | undefined) => {
      if (v != null && Number.isFinite(v)) {
        form.append(name, String(v));
      }
    };
    appendOpt('gpt_preserve_blues', options.gptPreserveBlues);
    appendOpt('gpt_detail_boost', options.gptDetailBoost);
    appendOpt('gpt_warmth_bias', options.gptWarmthBias);
    appendOpt('gpt_dehaze_strength', options.gptDehazeStrength);
    appendOpt('gpt_red_recovery_strength', options.gptRedRecoveryStrength);
    appendOpt('gpt_noise_reduction', options.gptNoiseReduction);

    const base = this.baseUrl.replace(/\/$/, '');
    const url = `${base}/process`;
    this.logger.debug(`Calling AI service: ${url}`);

    let response;
    try {
      response = await this.client.post<ArrayBuffer>(url, form, {
        headers: form.getHeaders(),
        responseType: 'arraybuffer',
        validateStatus: () => true,
      });
    } catch (err: any) {
      this.logger.warn(`AI service error: ${err?.message}`);
      throw new Error(
        err?.code === 'ECONNREFUSED'
          ? `Cannot reach AI service at ${url} (connection refused). Start ai-service/start.sh on the same port as AI_UNDERWATER_SERVICE_URL.`
          : err?.message || 'AI service request failed',
      );
    }

    if (response.status !== 200) {
      // Compatibility fallback: if AI_UNDERWATER_SERVICE_URL points to UVM/FastAPI
      // instead of ai-service, retry via UVM endpoint contract.
      if (response.status === 404) {
        try {
          const uvmUrl = `${base}/v1/process/photo/ai1`;
          const q = new URLSearchParams({
            strength: String(options.strength ?? 0.7),
          });
          if (options.depthMeters != null && Number.isFinite(options.depthMeters)) {
            q.set('depth_hint_m', String(options.depthMeters));
          }
          const fd = new FormData();
          fd.append('image', imageBuffer, {
            filename: 'image.jpg',
            contentType: 'image/jpeg',
          });
          const uvmResp = await this.client.post<{ image_jpeg_base64: string }>(
            `${uvmUrl}?${q.toString()}`,
            fd,
            {
              headers: fd.getHeaders(),
              validateStatus: () => true,
            },
          );
          if (uvmResp.status === 200 && uvmResp.data?.image_jpeg_base64) {
            return Buffer.from(uvmResp.data.image_jpeg_base64, 'hex');
          }
        } catch (e: any) {
          this.logger.warn(`UVM fallback failed: ${e?.message}`);
        }
      }
      let detail = `HTTP ${response.status}`;
      const raw = response.data as ArrayBuffer;
      if (raw && raw.byteLength > 0 && raw.byteLength < 8192) {
        try {
          const txt = Buffer.from(raw).toString('utf-8');
          const j = JSON.parse(txt) as { detail?: string | string[] };
          if (typeof j.detail === 'string') detail = j.detail;
          else if (Array.isArray(j.detail)) detail = j.detail.map((x) => String(x)).join('; ');
        } catch {
          detail = Buffer.from(raw).toString('utf-8').slice(0, 500);
        }
      }
      this.logger.warn(`AI service ${response.status}: ${detail}`);
      throw new Error(`AI service: ${detail}`);
    }

    return Buffer.from(response.data as ArrayBuffer);
  }
}
