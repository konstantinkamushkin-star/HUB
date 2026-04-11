import { Module } from '@nestjs/common';
import { UvmProxyController } from './uvm-proxy.controller';
import { UvmSeaSplatProxyController } from './uvm-seasplat-proxy.controller';
import { UvmVideoProxyController } from './uvm-video-proxy.controller';

@Module({
  controllers: [UvmProxyController, UvmSeaSplatProxyController, UvmVideoProxyController],
})
export class UvmProxyModule {}
