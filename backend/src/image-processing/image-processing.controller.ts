import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  StreamableFile,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { createReadStream } from 'fs';
import { ImageProcessingService } from './image-processing.service';
import { ProcessImageDto } from './dto/process-image.dto';

@Controller('v1/image')
export class ImageProcessingController {
  constructor(private readonly imageProcessing: ImageProcessingService) {}

  @Post('upload')
  @UseInterceptors(FileInterceptor('image', { limits: { fileSize: 35 * 1024 * 1024 } }))
  async upload(@UploadedFile() file: Express.Multer.File | undefined) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('Image file is required (field: image)');
    }
    return this.imageProcessing.saveUpload(file.buffer, file.originalname || 'upload.jpg');
  }

  @Post('process')
  async process(@Body() body: ProcessImageDto) {
    return this.imageProcessing.createJob(body);
  }

  @Get('status/:jobId')
  status(@Param('jobId') jobId: string) {
    return this.imageProcessing.getStatus(jobId);
  }

  @Get('result/:jobId')
  async result(@Param('jobId') jobId: string): Promise<StreamableFile> {
    const fp = await this.imageProcessing.getResultPath(jobId);
    const stream = createReadStream(fp);
    return new StreamableFile(stream, {
      type: 'image/jpeg',
      disposition: `inline; filename="result-${jobId}.jpg"`,
    });
  }
}
