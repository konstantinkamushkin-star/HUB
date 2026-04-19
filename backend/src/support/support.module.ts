import { Module, forwardRef } from '@nestjs/common';
import { AdminModule } from '../admin/admin.module';
import {
  PublicSupportTicketsController,
  PublicSupportTicketsV1Controller,
} from './public-support-tickets.controller';

@Module({
  imports: [forwardRef(() => AdminModule)],
  controllers: [PublicSupportTicketsController, PublicSupportTicketsV1Controller],
})
export class SupportModule {}
