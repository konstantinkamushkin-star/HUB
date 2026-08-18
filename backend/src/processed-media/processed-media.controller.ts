import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { RegisterProcessedMediaDto } from './dto/register-processed-media.dto';
import { ProcessedMediaService } from './processed-media.service';

@ApiTags('processed-media')
@Controller('processed-media')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class ProcessedMediaController {
  constructor(private readonly processedMedia: ProcessedMediaService) {}

  @Get()
  @ApiOperation({ summary: 'List my processing gallery' })
  list(
    @Request() req: { user: { sub: string } },
    @Query('limit') limit?: string,
  ) {
    const n = limit ? parseInt(limit, 10) : 60;
    return this.processedMedia.listMine(req.user.sub, Number.isFinite(n) ? n : 60);
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({
    summary: 'Register a processed photo/video in my gallery (counts toward admin stats)',
  })
  register(
    @Request() req: { user: { sub: string } },
    @Body() dto: RegisterProcessedMediaDto,
  ) {
    return this.processedMedia.register(req.user.sub, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Remove an item from my processing gallery' })
  remove(
    @Request() req: { user: { sub: string } },
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.processedMedia.remove(req.user.sub, id);
  }
}
