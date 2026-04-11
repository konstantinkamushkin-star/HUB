import {
  Injectable,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import * as fs from 'fs/promises';
import * as path from 'path';
import { randomUUID } from 'crypto';

const ALLOWED_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp']);
const MAX_BYTES = 15 * 1024 * 1024;

@Injectable()
export class MediaService {
  private readonly dir: string;

  constructor() {
    this.dir = path.join(process.cwd(), 'uploads', 'media');
    void fs.mkdir(this.dir, { recursive: true });
  }

  private safeName(stored: string): string {
    const base = path.basename(stored);
    if (!/^[a-f0-9-]{36}\.(jpe?g|png|webp)$/i.test(base)) {
      throw new NotFoundException('Not found');
    }
    return base;
  }

  async save(buffer: Buffer, originalName: string): Promise<{ path: string; url: string }> {
    if (!buffer?.length || buffer.length > MAX_BYTES) {
      throw new BadRequestException('Invalid or too large file');
    }
    const ext = (path.extname(originalName || '').toLowerCase() || '.jpg') as string;
    const norm = ext === '.jpeg' ? '.jpg' : ext;
    if (!ALLOWED_EXT.has(norm)) {
      throw new BadRequestException('Only jpg, png, webp are allowed');
    }
    const id = randomUUID();
    const filename = `${id}${norm}`;
    const fp = path.join(this.dir, filename);
    await fs.writeFile(fp, buffer);
    const publicPath = `/api/media/files/${filename}`;
    return { path: publicPath, url: publicPath };
  }

  async getFileStream(stored: string): Promise<{ stream: import('fs').ReadStream; mime: string }> {
    const name = this.safeName(stored);
    const fp = path.join(this.dir, name);
    const ext = path.extname(name).toLowerCase();
    const mime =
      ext === '.png'
        ? 'image/png'
        : ext === '.webp'
          ? 'image/webp'
          : 'image/jpeg';
    const { createReadStream } = await import('node:fs');
    try {
      await fs.stat(fp);
    } catch {
      throw new NotFoundException('Not found');
    }
    return { stream: createReadStream(fp), mime };
  }
}
