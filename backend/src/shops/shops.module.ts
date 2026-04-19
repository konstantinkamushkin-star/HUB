import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ShopsController } from './shops.controller';
import { ShopsService } from './shops.service';
import { ShopCommerceService } from './shop-commerce.service';
import { ShopEntity } from './entities/shop.entity';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [TypeOrmModule.forFeature([ShopEntity]), AuthModule],
  controllers: [ShopsController],
  providers: [ShopsService, ShopCommerceService],
  exports: [ShopsService],
})
export class ShopsModule {}
