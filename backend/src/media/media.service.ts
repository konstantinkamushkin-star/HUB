import {
  Injectable,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import * as fs from 'fs/promises';
import * as path from 'path';
import { randomUUID } from 'crypto';

const ALLOWED_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp', '.pdf', '.mp4', '.mov']);
const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
const MAX_VIDEO_BYTES = 250 * 1024 * 1024;

@Injectable()
export class MediaService {
  private readonly dir: string;

  constructor() {
    this.dir = path.join(process.cwd(), 'uploads', 'media');
    void fs.mkdir(this.dir, { recursive: true });
  }

  private safeName(stored: string): string {
    const base = path.basename(stored);
    if (!/^[a-f0-9-]{36}\.(jpe?g|png|webp|pdf|mp4|mov)$/i.test(base)) {
      throw new NotFoundException('Not found');
    }
    return base;
  }

  private mimeForExt(ext: string): string {
    if (ext === '.png') return 'image/png';
    if (ext === '.webp') return 'image/webp';
    if (ext === '.pdf') return 'application/pdf';
    if (ext === '.mp4') return 'video/mp4';
    if (ext === '.mov') return 'video/quicktime';
    return 'image/jpeg';
  }

  async save(buffer: Buffer, originalName: string): Promise<{ path: string; url: string }> {
    if (!buffer?.length) {
      throw new BadRequestException('Invalid or too large file');
    }
    const ext = (path.extname(originalName || '').toLowerCase() || '.jpg') as string;
    const norm = ext === '.jpeg' ? '.jpg' : ext;
    if (!ALLOWED_EXT.has(norm)) {
      throw new BadRequestException('Only jpg, png, webp, pdf, mp4, mov are allowed');
    }
    const maxBytes = norm === '.mp4' || norm === '.mov' ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
    if (buffer.length > maxBytes) {
      throw new BadRequestException('Invalid or too large file');
    }
    const id = randomUUID();
    const filename = `${id}${norm}`;
    const fp = path.join(this.dir, filename);
    await fs.writeFile(fp, buffer);
    const publicPath = `/api/media/files/${filename}`;
    return { path: publicPath, url: publicPath };
  }

  async getFileInfo(stored: string): Promise<{ filePath: string; mime: string; size: number }> {
    const name = this.safeName(stored);
    const fp = path.join(this.dir, name);
    const ext = path.extname(name).toLowerCase();
    try {
      const stat = await fs.stat(fp);
      return { filePath: fp, mime: this.mimeForExt(ext), size: stat.size };
    } catch {
      throw new NotFoundException('Not found');
    }
  }

  async getFileStream(stored: string): Promise<{ stream: import('fs').ReadStream; mime: string }> {
    const { filePath, mime } = await this.getFileInfo(stored);
    const { createReadStream } = await import('node:fs');
    return { stream: createReadStream(filePath), mime };
  }
}
