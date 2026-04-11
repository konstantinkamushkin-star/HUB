import { Module } from '@nestjs/common';
import { ImageProcessingController } from './image-processing.controller';
import { ImageProcessingService } from './image-processing.service';
import { UnderwaterAiModule } from '../underwater-ai/underwater-ai.module';

@Module({
  imports: [UnderwaterAiModule],
  controllers: [ImageProcessingController],
  providers: [ImageProcessingService],
})
export class ImageProcessingModule {}
