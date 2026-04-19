import { Module, forwardRef } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { AdminVerificationRequestEntity } from '../admin/entities/admin-verification-request.entity';
import { AdminModule } from '../admin/admin.module';
import { PartnerRegistrationService } from './partner-registration.service';
import { PartnerRegistrationController } from './partner-registration.controller';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      DiveCenterEntity,
      ShopEntity,
      AdminVerificationRequestEntity,
    ]),
    forwardRef(() => AdminModule),
  ],
  controllers: [PartnerRegistrationController],
  providers: [PartnerRegistrationService],
})
export class PartnerModule {}
