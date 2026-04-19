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
import { ChatModule } from '../chat/chat.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { PushModule } from '../push/push.module';
import { AdminDiveSiteContributionsController } from '../admin/admin-dive-site-contributions.controller';
import { AdminOrSuperAdminGuard } from '../admin/admin-or-super-admin.guard';

@Module({
  imports: [
    TypeOrmModule.forFeature([DiveSiteEntity, DiveSiteContributionEntity]),
    AuthModule,
    ChatModule,
    NotificationsModule,
    PushModule,
  ],
  controllers: [
    DiveSitesController,
    LegacyDiveSitesController,
    DiveSiteContributionsController,
    AdminDiveSiteContributionsController,
  ],
  providers: [DiveSitesService, DiveSiteContributionsService, AdminOrSuperAdminGuard],
  exports: [DiveSitesService, DiveSiteContributionsService],
})
export class DiveSitesModule {}
