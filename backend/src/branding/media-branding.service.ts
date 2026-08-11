import { Injectable, Logger } from '@nestjs/common';
import { spawn } from 'node:child_process';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { randomUUID } from 'node:crypto';
import sharp from 'sharp';

const CAPTION = 'colored by DiveHub';
const OPACITY = 0.5;

/**
 * DiveHub brand mark + “colored by DiveHub” (50% opacity, bottom-trailing).
 * Applied to cloud-enhanced photo/video responses so clients receive branded media.
 */
@Injectable()
export class MediaBrandingService {
  private readonly logger = new Logger(MediaBrandingService.name);
  private readonly logoPath: string;

  constructor() {
    this.logoPath = path.join(process.cwd(), 'assets', 'brand_logo_mask.png');
  }

  async watermarkJpeg(jpeg: Buffer): Promise<Buffer> {
    try {
      if (!jpeg?.length) return jpeg;
      const image = sharp(jpeg, { failOn: 'none' });
      const meta = await image.metadata();
      const width = meta.width ?? 0;
      const height = meta.height ?? 0;
      if (width < 32 || height < 32) return jpeg;

      const overlay = await this.buildOverlayPng(width, height);
      return await image
        .composite([{ input: overlay, left: 0, top: 0 }])
        .jpeg({ quality: 92, mozjpeg: true })
        .toBuffer();
    } catch (e: any) {
      this.logger.warn(`watermarkJpeg skipped: ${e?.message || e}`);
      return jpeg;
    }
  }

  /**
   * Burns branding into every frame. Requires `ffmpeg` on PATH.
   * Returns original buffer if ffmpeg is missing or branding fails.
   */
  async watermarkMp4(mp4: Buffer): Promise<Buffer> {
    try {
      if (!mp4?.length || mp4.length < 32) return mp4;
      const hasFfmpeg = await this.commandExists('ffmpeg');
      if (!hasFfmpeg) {
        this.logger.warn('ffmpeg not found — video watermark skipped');
        return mp4;
      }

      const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'divehub-brand-'));
      const inPath = path.join(tmp, `in_${randomUUID()}.mp4`);
      const outPath = path.join(tmp, `out_${randomUUID()}.mp4`);
      const overlayPath = path.join(tmp, `overlay_${randomUUID()}.png`);
      try {
        await fs.writeFile(inPath, mp4);
        const { width, height } = await this.probeVideoSize(inPath);
        if (width < 32 || height < 32) return mp4;
        const overlay = await this.buildOverlayPng(width, height);
        await fs.writeFile(overlayPath, overlay);

        await this.runFfmpeg([
          '-y',
          '-i',
          inPath,
          '-i',
          overlayPath,
          '-filter_complex',
          '[0:v][1:v]overlay=0:0:format=auto',
          '-c:v',
          'libx264',
          '-preset',
          'veryfast',
          '-crf',
          '23',
          '-c:a',
          'copy',
          '-movflags',
          '+faststart',
          outPath,
        ]);
        const branded = await fs.readFile(outPath);
        return branded.length > 0 ? branded : mp4;
      } finally {
        await fs.rm(tmp, { recursive: true, force: true }).catch(() => undefined);
      }
    } catch (e: any) {
      this.logger.warn(`watermarkMp4 skipped: ${e?.message || e}`);
      return mp4;
    }
  }

  /** Hex-encoded JPEG (UVM `image_jpeg_base64` contract). */
  async watermarkJpegHex(hex: string): Promise<string> {
    const raw = Buffer.from(String(hex || '').trim(), 'hex');
    if (!raw.length) return hex;
    const branded = await this.watermarkJpeg(raw);
    return branded.toString('hex');
  }

  private async buildOverlayPng(width: number, height: number): Promise<Buffer> {
    const w = Math.max(2, Math.floor(width));
    const h = Math.max(2, Math.floor(height));
    const margin = Math.max(8, Math.round(w * 0.03));
    const gap = Math.max(4, Math.round(w * 0.012));
    const fontSize = Math.min(42, Math.max(18, Math.round(w * 0.028)));
    const textWidthApprox = Math.round(CAPTION.length * fontSize * 0.55);
    const textHeight = Math.round(fontSize * 1.25);
    const logoTargetH = Math.min(56, Math.max(22, Math.round(textHeight * 1.35)));

    let logoBuf: Buffer | null = null;
    let logoW = 0;
    let logoH = 0;
    try {
      await fs.access(this.logoPath);
      const resized = await sharp(this.logoPath)
        .resize({ height: logoTargetH, fit: 'inside' })
        .ensureAlpha()
        .png()
        .toBuffer({ resolveWithObject: true });
      logoW = resized.info.width;
      logoH = resized.info.height;
      // Bake 50% opacity into logo alpha.
      const { data, info } = await sharp(resized.data)
        .ensureAlpha()
        .raw()
        .toBuffer({ resolveWithObject: true });
      for (let i = 3; i < data.length; i += 4) {
        data[i] = Math.round(data[i] * OPACITY);
      }
      logoBuf = await sharp(data, {
        raw: { width: info.width, height: info.height, channels: 4 },
      })
        .png()
        .toBuffer();
    } catch {
      logoBuf = null;
      logoW = 0;
      logoH = 0;
    }

    const blockW = logoW + (logoBuf ? gap : 0) + textWidthApprox;
    const blockH = Math.max(logoH || 0, textHeight);
    const left = Math.max(0, w - blockW - margin);
    const top = Math.max(0, h - blockH - margin);
    const textX = left + logoW + (logoBuf ? gap : 0);
    const textY = top + Math.round((blockH + fontSize) / 2);
    const shadow = Math.max(1, Math.round(fontSize * 0.15));

    const svg = Buffer.from(
      `<svg width="${w}" height="${h}" xmlns="http://www.w3.org/2000/svg">
  <style>
    .cap { fill: #ffffff; fill-opacity: ${OPACITY}; font-family: Arial, Helvetica, sans-serif;
           font-size: ${fontSize}px; font-weight: 400;
           filter: drop-shadow(0 ${Math.max(1, Math.round(fontSize * 0.06))}px ${shadow}px rgba(0,0,0,0.45)); }
  </style>
  <text x="${textX}" y="${textY}" class="cap">${CAPTION}</text>
</svg>`,
    );

    const composites: Array<{ input: Buffer; left: number; top: number }> = [];
    if (logoBuf) {
      composites.push({
        input: logoBuf,
        left: Math.round(left),
        top: Math.round(top + (blockH - logoH) / 2),
      });
    }
    composites.push({ input: svg, left: 0, top: 0 });

    return await sharp({
      create: {
        width: w,
        height: h,
        channels: 4,
        background: { r: 0, g: 0, b: 0, alpha: 0 },
      },
    })
      .composite(composites)
      .png()
      .toBuffer();
  }

  private async probeVideoSize(filePath: string): Promise<{ width: number; height: number }> {
    const hasProbe = await this.commandExists('ffprobe');
    if (!hasProbe) return { width: 1280, height: 720 };
    const out = await this.runCapture('ffprobe', [
      '-v',
      'error',
      '-select_streams',
      'v:0',
      '-show_entries',
      'stream=width,height',
      '-of',
      'csv=p=0:s=x',
      filePath,
    ]);
    const m = /^(\d+)x(\d+)/.exec(out.trim());
    if (!m) return { width: 1280, height: 720 };
    return { width: parseInt(m[1], 10), height: parseInt(m[2], 10) };
  }

  private runFfmpeg(args: string[]): Promise<void> {
    return new Promise((resolve, reject) => {
      const child = spawn('ffmpeg', args, { stdio: ['ignore', 'ignore', 'pipe'] });
      let err = '';
      child.stderr?.on('data', (d) => {
        err += d.toString();
      });
      child.on('error', reject);
      child.on('close', (code) => {
        if (code === 0) resolve();
        else reject(new Error(err.slice(-800) || `ffmpeg exit ${code}`));
      });
    });
  }

  private runCapture(cmd: string, args: string[]): Promise<string> {
    return new Promise((resolve, reject) => {
      const child = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
      let out = '';
      let err = '';
      child.stdout?.on('data', (d) => {
        out += d.toString();
      });
      child.stderr?.on('data', (d) => {
        err += d.toString();
      });
      child.on('error', reject);
      child.on('close', (code) => {
        if (code === 0) resolve(out);
        else reject(new Error(err.slice(-400) || `${cmd} exit ${code}`));
      });
    });
  }

  private commandExists(bin: string): Promise<boolean> {
    return new Promise((resolve) => {
      const child = spawn(bin, ['-version'], { stdio: 'ignore' });
      child.on('error', () => resolve(false));
      child.on('close', (code) => resolve(code === 0));
    });
  }
}
