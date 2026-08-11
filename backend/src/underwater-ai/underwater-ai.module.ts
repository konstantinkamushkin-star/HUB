import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { UnderwaterAiController } from './underwater-ai.controller';
import { UnderwaterAiService } from './underwater-ai.service';
import { BrandingModule } from '../branding/branding.module';

@Module({
  imports: [ConfigModule, BrandingModule],
  controllers: [UnderwaterAiController],
  providers: [UnderwaterAiService],
  exports: [UnderwaterAiService],
})
export class UnderwaterAiModule {}
