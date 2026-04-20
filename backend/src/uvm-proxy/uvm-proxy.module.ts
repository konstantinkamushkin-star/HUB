import { Module } from '@nestjs/common';
import { UvmProxyController } from './uvm-proxy.controller';
import { UvmVideoProxyController } from './uvm-video-proxy.controller';

@Module({
  controllers: [UvmProxyController, UvmVideoProxyController],
})
export class UvmProxyModule {}
