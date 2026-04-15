import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../auth/auth.module';
import { CenterServicesController } from './center-services.controller';
import { CenterServicesService } from './center-services.service';

@Module({
  imports: [TypeOrmModule.forFeature([]), AuthModule],
  controllers: [CenterServicesController],
  providers: [CenterServicesService],
  exports: [CenterServicesService],
})
export class CenterServicesModule {}
