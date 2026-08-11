import {
  BadRequestException,
  Controller,
  Get,
  Param,
  Post,
  Req,
  Res,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import {
  ApiBearerAuth,
  ApiBody,
  ApiConsumes,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import type { Request, Response } from 'express';
import { createReadStream } from 'node:fs';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { MediaService } from './media.service';

@ApiTags('media')
@Controller('media')
export class MediaController {
  constructor(private readonly media: MediaService) {}

  @Post('upload')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Upload image or video for feed / chat (returns relative URL path)' })
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      properties: { file: { type: 'string', format: 'binary' } },
    },
  })
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      // Processed underwater clips (≤60s) can exceed 100MB before client re-encode.
      limits: { fileSize: 250 * 1024 * 1024 },
    }),
  )
  async upload(@UploadedFile() file: Express.Multer.File | undefined) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('file required (field: file)');
    }
    return this.media.save(file.buffer, file.originalname || 'photo.jpg');
  }

  @SkipThrottle()
  @Get('files/:name')
  @ApiOperation({ summary: 'Public read of uploaded media (opaque filename)' })
  async file(
    @Param('name') name: string,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    const { filePath, mime, size } = await this.media.getFileInfo(name);
    const range = req.headers.range;

    if (range) {
      const match = /^bytes=(\d*)-(\d*)$/i.exec(range);
      if (match) {
        const start = match[1] ? parseInt(match[1], 10) : 0;
        const end = match[2] ? parseInt(match[2], 10) : size - 1;
        if (Number.isNaN(start) || Number.isNaN(end) || start > end || start >= size) {
          res.status(416).set({ 'Content-Range': `bytes */${size}` }).end();
          return;
        }
        const chunkSize = end - start + 1;
        res.status(206);
        res.set({
          'Content-Range': `bytes ${start}-${end}/${size}`,
          'Accept-Ranges': 'bytes',
          'Content-Length': String(chunkSize),
          'Content-Type': mime,
          'Content-Disposition': 'inline',
        });
        createReadStream(filePath, { start, end }).pipe(res);
        return;
      }
    }

    res.status(200);
    res.set({
      'Accept-Ranges': 'bytes',
      'Content-Length': String(size),
      'Content-Type': mime,
      'Content-Disposition': 'inline',
    });
    createReadStream(filePath).pipe(res);
  }
}
