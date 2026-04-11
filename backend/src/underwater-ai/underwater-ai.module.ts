import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { UnderwaterAiController } from './underwater-ai.controller';
import { UnderwaterAiService } from './underwater-ai.service';

@Module({
  imports: [ConfigModule],
  controllers: [UnderwaterAiController],
  providers: [UnderwaterAiService],
  exports: [UnderwaterAiService],
})
export class UnderwaterAiModule {}
