import {
  BadGatewayException,
  BadRequestException,
  Controller,
  Param,
  Post,
  Query,
  Res,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import axios, { AxiosError } from 'axios';
import FormData from 'form-data';
import type { Response } from 'express';

/**
 * Проксирует тот же контракт, что у Python UVM/FastAPI:
 * POST multipart поле `image`, query `strength`, `depth_hint_m`.
 * iOS может указывать базой REST API (https://api/...) без порта 8010.
 */
@Controller('v1/process/photo')
export class UvmProxyController {
  @Post(':engine')
  @UseInterceptors(
    FileInterceptor('image', {
      limits: { fileSize: 40 * 1024 * 1024 },
    }),
  )
  async forward(
    @Param('engine') engine: string,
    @UploadedFile() file: Express.Multer.File | undefined,
    @Query('strength') strength: string | undefined,
    @Query('depth_hint_m') depthHintM: string | undefined,
    @Query('quality') quality: string | undefined,
    @Query('mode') mode: string | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('multipart field "image" is required');
    }

    const uvm = (process.env.UVM_URL || 'http://127.0.0.1:8010').replace(/\/$/, '');
    const eng = encodeURIComponent((engine || '').trim().toLowerCase());
    const qs = new URLSearchParams();
    if (strength != null && strength !== '') qs.set('strength', strength);
    if (depthHintM != null && depthHintM !== '') qs.set('depth_hint_m', depthHintM);
    if (quality != null && quality !== '') qs.set('quality', quality);
    if (mode != null && mode !== '') qs.set('mode', mode);
    const q = qs.toString();
    const url = `${uvm}/v1/process/photo/${eng}${q ? `?${q}` : ''}`;

    const fd = new FormData();
    fd.append('image', file.buffer, {
      filename: file.originalname || 'photo.jpg',
      contentType: file.mimetype || 'image/jpeg',
    });

    try {
      const r = await axios.post(url, fd, {
        headers: fd.getHeaders(),
        timeout: 180_000,
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        validateStatus: () => true,
      });
      res.status(r.status);
      return r.data;
    } catch (e) {
      const err = e as AxiosError;
      if (err.code === 'ECONNREFUSED' || err.code === 'ENOTFOUND') {
        throw new BadGatewayException(
          `Cannot reach UVM at ${uvm} (set UVM_URL env if Python service runs elsewhere)`,
        );
      }
      throw new BadGatewayException(err.message || 'UVM proxy request failed');
    }
  }
}
