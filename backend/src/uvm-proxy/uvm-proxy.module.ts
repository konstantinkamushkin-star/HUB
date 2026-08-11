import { Module } from '@nestjs/common';
import { UvmProxyController } from './uvm-proxy.controller';
import { UvmVideoProxyController } from './uvm-video-proxy.controller';
import { BrandingModule } from '../branding/branding.module';

@Module({
  imports: [BrandingModule],
  controllers: [UvmProxyController, UvmVideoProxyController],
})
export class UvmProxyModule {}
