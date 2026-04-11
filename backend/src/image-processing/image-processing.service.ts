import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import * as fs from 'fs/promises';
import * as path from 'path';
import * as os from 'os';
import { randomUUID } from 'crypto';
import { UnderwaterAiService, type ProcessUnderwaterOptions } from '../underwater-ai/underwater-ai.service';
import { ProcessImageDto } from './dto/process-image.dto';

type JobStatus = 'queued' | 'processing' | 'done' | 'failed';

interface JobRecord {
  id: string;
  imageId: string;
  status: JobStatus;
  progress: number;
  error?: string;
  resultPath?: string;
  createdAt: number;
}

@Injectable()
export class ImageProcessingService {
  private readonly logger = new Logger(ImageProcessingService.name);
  private readonly uploads = new Map<string, { path: string; expiresAt: number }>();
  private readonly jobs = new Map<string, JobRecord>();
  private readonly workDir: string;

  constructor(private readonly underwaterAi: UnderwaterAiService) {
    this.workDir = path.join(os.tmpdir(), 'divehub-image-processing');
    void this.ensureWorkDir();
    const sweep = setInterval(() => this.sweepExpired(), 60_000);
    if (typeof (sweep as any).unref === 'function') (sweep as any).unref();
  }

  private async ensureWorkDir() {
    await fs.mkdir(this.workDir, { recursive: true });
  }

  private sweepExpired() {
    const now = Date.now();
    for (const [id, u] of this.uploads.entries()) {
      if (u.expiresAt < now) {
        void fs.unlink(u.path).catch(() => {});
        this.uploads.delete(id);
      }
    }
    for (const [id, j] of this.jobs.entries()) {
      if (now - j.createdAt > 3_600_000) {
        if (j.resultPath) void fs.unlink(j.resultPath).catch(() => {});
        this.jobs.delete(id);
      }
    }
  }

  async saveUpload(buffer: Buffer, originalName: string): Promise<{ image_id: string }> {
    await this.ensureWorkDir();
    const id = randomUUID();
    const ext = path.extname(originalName || '') || '.jpg';
    const fp = path.join(this.workDir, `up_${id}${ext}`);
    await fs.writeFile(fp, buffer);
    const expiresAt = Date.now() + 3_600_000;
    this.uploads.set(id, { path: fp, expiresAt });
    return { image_id: id };
  }

  createJob(dto: ProcessImageDto): { job_id: string; status: JobStatus } {
    const upload = this.uploads.get(dto.image_id);
    if (!upload) {
      throw new BadRequestException('Unknown or expired image_id. Upload again.');
    }
    const jobId = randomUUID();
    const rec: JobRecord = {
      id: jobId,
      imageId: dto.image_id,
      status: 'queued',
      progress: 0,
      createdAt: Date.now(),
    };
    this.jobs.set(jobId, rec);
    setImmediate(() => this.runJob(jobId, dto).catch((e) => this.logger.error(e)));
    return { job_id: jobId, status: 'queued' };
  }

  getStatus(jobId: string): { job_id: string; status: JobStatus; progress: number; error?: string } {
    const j = this.jobs.get(jobId);
    if (!j) throw new NotFoundException('job_id not found');
    const row: { job_id: string; status: JobStatus; progress: number; error?: string } = {
      job_id: j.id,
      status: j.status,
      progress: j.progress,
    };
    if (j.status === 'failed' && j.error) row.error = j.error;
    return row;
  }

  async getResultPath(jobId: string): Promise<string> {
    const j = this.jobs.get(jobId);
    if (!j) throw new NotFoundException('job_id not found');
    if (j.status !== 'done' || !j.resultPath) {
      throw new BadRequestException(`Job not ready: ${j.status}`);
    }
    return j.resultPath;
  }

  private async runJob(jobId: string, dto: ProcessImageDto) {
    const job = this.jobs.get(jobId);
    const upload = this.uploads.get(dto.image_id);
    if (!job || !upload) return;

    job.status = 'processing';
    job.progress = 10;

    try {
      const buf = await fs.readFile(upload.path);
      job.progress = 40;

      if (!this.underwaterAi.isAvailable()) {
        job.status = 'failed';
        job.progress = 0;
        job.error = 'Cloud AI is not configured (AI_UNDERWATER_SERVICE_URL).';
        return;
      }

      const p = dto.params;
      const depthMeters = Math.max(0, Math.min(60, (p.depth / 100) * 40));
      const strengthRaw = (p.strength + p.dehaze + p.clarity) / 300;
      const strength = Math.max(0.25, Math.min(1, strengthRaw + Math.abs(p.temperature) / 500));
      const rawPipeline = dto.pipeline ?? p.pipeline;
      const pipeline = (rawPipeline || 'default').trim().toLowerCase();
      const useAi = pipeline === 'default' && p.auto_ai !== false;

      const isGpt = pipeline === 'gpt';
      /** Только явные поля API; иначе Python использует дефолты GptRestoreParams (стабильный GPT). */
      const gptOpts: Partial<
        Pick<
          ProcessUnderwaterOptions,
          | 'gptPreserveBlues'
          | 'gptDetailBoost'
          | 'gptWarmthBias'
          | 'gptDehazeStrength'
          | 'gptRedRecoveryStrength'
          | 'gptNoiseReduction'
        >
      > = {};
      if (isGpt) {
        if (p.gpt_preserve_blues != null) gptOpts.gptPreserveBlues = p.gpt_preserve_blues;
        if (p.gpt_detail_boost != null) gptOpts.gptDetailBoost = p.gpt_detail_boost;
        if (p.gpt_warmth_bias != null) gptOpts.gptWarmthBias = p.gpt_warmth_bias;
        if (p.gpt_dehaze_strength != null) gptOpts.gptDehazeStrength = p.gpt_dehaze_strength;
        if (p.gpt_red_recovery_strength != null)
          gptOpts.gptRedRecoveryStrength = p.gpt_red_recovery_strength;
        if (p.gpt_noise_reduction != null) gptOpts.gptNoiseReduction = p.gpt_noise_reduction;
      }

      const out = await this.underwaterAi.processImage(buf, {
        depthMeters,
        strength,
        useAi,
        pipeline: pipeline || 'default',
        ...gptOpts,
      });

      job.progress = 90;
      const outPath = path.join(this.workDir, `out_${jobId}.jpg`);
      await fs.writeFile(outPath, out);
      job.resultPath = outPath;
      job.status = 'done';
      job.progress = 100;
    } catch (e: any) {
      this.logger.warn(`Job ${jobId} failed: ${e?.message}`);
      job.status = 'failed';
      job.progress = 0;
      job.error = e?.message || 'processing_failed';
    }
  }
}
