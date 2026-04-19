import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DiveSitesController } from './dive-sites.controller';
import { LegacyDiveSitesController } from './legacy-dive-sites.controller';
import { DiveSitesService } from './dive-sites.service';
import { DiveSiteEntity } from './entities/dive-site.entity';
import { DiveSiteContributionEntity } from './entities/dive-site-contribution.entity';
import { DiveSiteContributionsService } from './dive-site-contributions.service';
import { DiveSiteContributionsController } from './dive-site-contributions.controller';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([DiveSiteEntity, DiveSiteContributionEntity]),
    AuthModule,
  ],
  controllers: [
    DiveSitesController,
    LegacyDiveSitesController,
    DiveSiteContributionsController,
  ],
  providers: [DiveSitesService, DiveSiteContributionsService],
  exports: [DiveSitesService, DiveSiteContributionsService],
})
export class DiveSitesModule {}
