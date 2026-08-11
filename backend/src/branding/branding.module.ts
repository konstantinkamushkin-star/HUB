import { Module } from '@nestjs/common';
import { MediaBrandingService } from './media-branding.service';

@Module({
  providers: [MediaBrandingService],
  exports: [MediaBrandingService],
})
export class BrandingModule {}
