import {
  BadGatewayException,
  BadRequestException,
  Controller,
  Param,
  Post,
  Res,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import axios, { AxiosError } from 'axios';
import FormData from 'form-data';
import type { Response } from 'express';

/**
 * Проксирует Python UVM: POST multipart поле `image` (Nikolaj Bech — без query, как upstream).
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
    @Res({ passthrough: true }) res: Response,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('multipart field "image" is required');
    }

    const uvm = (process.env.UVM_URL || 'http://127.0.0.1:8010').replace(/\/$/, '');
    const eng = encodeURIComponent((engine || '').trim().toLowerCase());
    const url = `${uvm}/v1/process/photo/${eng}`;

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
