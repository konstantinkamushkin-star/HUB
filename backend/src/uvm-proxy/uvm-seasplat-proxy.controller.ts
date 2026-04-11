import {
  BadGatewayException,
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  Res,
  UploadedFiles,
  UseInterceptors,
} from '@nestjs/common';
import { FilesInterceptor } from '@nestjs/platform-express';
import axios, { AxiosError } from 'axios';
import FormData from 'form-data';
import type { Response } from 'express';

@Controller('v1/seasplat')
export class UvmSeaSplatProxyController {
  @Post('scenes')
  @UseInterceptors(
    FilesInterceptor('images', 128, {
      limits: { fileSize: 40 * 1024 * 1024 },
    }),
  )
  async uploadScene(
    @UploadedFiles() files: Express.Multer.File[] | undefined,
    @Query('poses_json') posesJson: string | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    if (!files?.length) {
      throw new BadRequestException('multipart field "images" is required');
    }
    const url = this.buildUvmURL('/v1/seasplat/scenes', posesJson ? { poses_json: posesJson } : undefined);
    const fd = new FormData();
    for (const file of files) {
      fd.append('images', file.buffer, {
        filename: file.originalname || 'frame.jpg',
        contentType: file.mimetype || 'image/jpeg',
      });
    }
    const r = await this.postForward(url, fd, fd.getHeaders());
    res.status(r.status);
    return r.data;
  }

  @Get('scenes/:sceneId')
  async sceneStatus(@Param('sceneId') sceneId: string, @Res({ passthrough: true }) res: Response) {
    const url = this.buildUvmURL(`/v1/seasplat/scenes/${encodeURIComponent(sceneId)}`);
    const r = await this.getForward(url);
    res.status(r.status);
    return r.data;
  }

  @Post('jobs')
  async runJob(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    const url = this.buildUvmURL('/v1/seasplat/jobs');
    const r = await this.postForward(url, body, { 'Content-Type': 'application/json' });
    res.status(r.status);
    return r.data;
  }

  @Get('jobs/:jobId')
  async jobStatus(@Param('jobId') jobId: string, @Res({ passthrough: true }) res: Response) {
    const url = this.buildUvmURL(`/v1/seasplat/jobs/${encodeURIComponent(jobId)}`);
    const r = await this.getForward(url);
    res.status(r.status);
    return r.data;
  }

  @Get('jobs/:jobId/render')
  async render(@Param('jobId') jobId: string, @Res({ passthrough: true }) res: Response) {
    const url = this.buildUvmURL(`/v1/seasplat/jobs/${encodeURIComponent(jobId)}/render`);
    const r = await this.getForward(url);
    res.status(r.status);
    return r.data;
  }

  private buildUvmURL(path: string, query?: Record<string, string>) {
    const uvm = (process.env.UVM_URL || 'http://127.0.0.1:8010').replace(/\/$/, '');
    const qs = new URLSearchParams(query || {}).toString();
    return `${uvm}${path}${qs ? `?${qs}` : ''}`;
  }

  private async postForward(url: string, data: any, headers: Record<string, string>) {
    try {
      return await axios.post(url, data, {
        headers,
        timeout: 180_000,
        maxBodyLength: Infinity,
        maxContentLength: Infinity,
        validateStatus: () => true,
      });
    } catch (e) {
      const err = e as AxiosError;
      if (err.code === 'ECONNREFUSED' || err.code === 'ENOTFOUND') {
        throw new BadGatewayException(`Cannot reach UVM at ${url}`);
      }
      throw new BadGatewayException(err.message || 'UVM SeaSplat proxy request failed');
    }
  }

  private async getForward(url: string) {
    try {
      return await axios.get(url, {
        timeout: 90_000,
        validateStatus: () => true,
      });
    } catch (e) {
      const err = e as AxiosError;
      if (err.code === 'ECONNREFUSED' || err.code === 'ENOTFOUND') {
        throw new BadGatewayException(`Cannot reach UVM at ${url}`);
      }
      throw new BadGatewayException(err.message || 'UVM SeaSplat proxy request failed');
    }
  }
}
