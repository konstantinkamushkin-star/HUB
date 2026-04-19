import { Body, Controller, HttpCode, HttpStatus, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { TranslateService } from './translate.service';
import {
  TranslateBatchRequestDto,
  TranslateRequestDto,
} from './dto/translate.dto';

@ApiTags('translate')
@Controller('translate')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class TranslateController {
  constructor(private readonly translateService: TranslateService) {}

  @Post()
  @HttpCode(HttpStatus.OK)
  async translateOne(@Body() dto: TranslateRequestDto) {
    const translatedText = await this.translateService.translate(
      dto.text,
      dto.sourceLanguage,
      dto.targetLanguage,
    );
    return { translatedText };
  }

  @Post('batch')
  @HttpCode(HttpStatus.OK)
  async translateBatch(@Body() dto: TranslateBatchRequestDto) {
    const translatedTexts = await this.translateService.translateBatch(
      dto.texts,
      dto.sourceLanguage,
      dto.targetLanguage,
    );
    return { translatedTexts };
  }
}
