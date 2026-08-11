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
import { MediaBrandingService } from '../branding/media-branding.service';

/**
 * Проксирует Python UVM: POST multipart `image` + query `strength` (как в App Store / DiveHub iOS).
 * After a successful enhance, burns DiveHub branding into the JPEG before returning.
 */
@Controller('v1/process/photo')
export class UvmProxyController {
  constructor(private readonly branding: MediaBrandingService) {}

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
    @Query('mode') mode: string | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('multipart field "image" is required');
    }

    const uvm = (process.env.UVM_URL || 'http://127.0.0.1:8010').replace(/\/$/, '');
    const eng = encodeURIComponent((engine || '').trim().toLowerCase());
    const params = new URLSearchParams();
    if (strength != null && strength !== '') {
      params.set('strength', strength);
    }
    if (depthHintM != null && depthHintM !== '') {
      params.set('depth_hint_m', depthHintM);
    }
    if (mode != null && mode.trim() !== '') {
      params.set('mode', mode.trim());
    }
    const qs = params.toString();
    const url = `${uvm}/v1/process/photo/${eng}${qs ? `?${qs}` : ''}`;

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
      if (r.status >= 200 && r.status < 300 && r.data && typeof r.data === 'object') {
        const body = r.data as { image_jpeg_base64?: string; report?: unknown };
        if (typeof body.image_jpeg_base64 === 'string' && body.image_jpeg_base64.length > 0) {
          body.image_jpeg_base64 = await this.branding.watermarkJpegHex(body.image_jpeg_base64);
        }
        return body;
      }
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
