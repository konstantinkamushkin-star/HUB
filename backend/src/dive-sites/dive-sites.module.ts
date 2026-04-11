import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveSitesController } from './dive-sites.controller';
import { LegacyDiveSitesController } from './legacy-dive-sites.controller';
import { DiveSitesService } from './dive-sites.service';
import { DiveSiteEntity } from './entities/dive-site.entity';

@Module({
  imports: [TypeOrmModule.forFeature([DiveSiteEntity])],
  controllers: [DiveSitesController, LegacyDiveSitesController],
  providers: [DiveSitesService],
  exports: [DiveSitesService],
})
export class DiveSitesModule {}
