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

@Controller('v1/process/video')
export class UvmVideoProxyController {
  @Post(':engine')
  @UseInterceptors(
    FileInterceptor('video', {
      limits: { fileSize: 500 * 1024 * 1024 },
    }),
  )
  async forward(
    @Param('engine') engine: string,
    @UploadedFile() file: Express.Multer.File | undefined,
    @Query('max_side') maxSide: string | undefined,
    @Res() res: Response,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('multipart field "video" is required');
    }

    const uvm = (process.env.UVM_URL || 'http://127.0.0.1:8010').replace(/\/$/, '');
    const eng = encodeURIComponent((engine || '').trim().toLowerCase());
    const qs = new URLSearchParams();
    if (maxSide != null && maxSide !== '') qs.set('max_side', maxSide);
    const q = qs.toString();
    const url = `${uvm}/v1/process/video/${eng}${q ? `?${q}` : ''}`;

    const fd = new FormData();
    fd.append('video', file.buffer, {
      filename: file.originalname || 'video.mp4',
      contentType: file.mimetype || 'video/mp4',
    });

    const timeoutMs = (() => {
      const raw = process.env.UVM_VIDEO_PROXY_TIMEOUT_MS?.trim();
      if (raw) {
        const n = parseInt(raw, 10);
        if (Number.isFinite(n) && n >= 120_000) return n;
      }
      // 5+ min source video can exceed 10 min wall time (upload + CPU + download).
      return 3_600_000;
    })();

    try {
      const r = await axios.post<ArrayBuffer>(url, fd, {
        headers: fd.getHeaders(),
        timeout: timeoutMs,
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        responseType: 'arraybuffer',
        validateStatus: () => true,
      });
      res.status(r.status);
      if (r.status >= 200 && r.status < 300) {
        res.setHeader('Content-Type', r.headers['content-type'] || 'video/mp4');
        return res.send(Buffer.from(r.data));
      }
      const bodyText = Buffer.from(r.data).toString('utf-8');
      return res.send(bodyText);
    } catch (e) {
      const err = e as AxiosError;
      if (err.code === 'ECONNREFUSED' || err.code === 'ENOTFOUND') {
        throw new BadGatewayException(
          `Cannot reach UVM at ${uvm} (set UVM_URL env if Python service runs elsewhere)`,
        );
      }
      throw new BadGatewayException(err.message || 'UVM video proxy request failed');
    }
  }
}
