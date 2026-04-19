import { Module } from '@nestjs/common';
import { APP_FILTER } from '@nestjs/core';
import { TypeOrmModule } from '@nestjs/typeorm';
import { PushModule } from '../push/push.module';
import { AdminController } from './admin.controller';
import { ErrorStatsService } from './error-stats.service';
import { ErrorTrackingFilter } from './error-tracking.filter';
import { SuperAdminGuard } from './super-admin.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AuditLogService } from './audit-log.service';
import { AdminAuditLogEntity } from './entities/audit-log.entity';
import { User } from '../users/entities/user.entity';
import { AdminUsersController } from './admin-users.controller';
import { AdminUsersService } from './admin-users.service';
import { AdminAuditController } from './admin-audit.controller';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { ShopEntity } from '../shops/entities/shop.entity';
import { AdminModerationService } from './admin-moderation.service';
import { AdminModerationController } from './admin-moderation.controller';
import { AdminReportEntity } from './entities/admin-report.entity';
import { AdminReportsController } from './admin-reports.controller';
import { AdminReportsService } from './admin-reports.service';
import { AdminFeatureFlagEntity } from './entities/feature-flag.entity';
import { AdminSystemSettingEntity } from './entities/system-setting.entity';
import { AdminNotificationCampaignEntity } from './entities/notification-campaign.entity';
import { AdminFeatureFlagsService } from './admin-feature-flags.service';
import { AdminSystemSettingsService } from './admin-system-settings.service';
import { AdminNotificationsService } from './admin-notifications.service';
import { AdminFeatureFlagsController } from './admin-feature-flags.controller';
import { AdminSystemSettingsController } from './admin-system-settings.controller';
import { AdminNotificationsController } from './admin-notifications.controller';
import { AdminRolesController } from './admin-roles.controller';
import { AdminComplianceRequestEntity } from './entities/admin-compliance-request.entity';
import { AdminDataJobEntity } from './entities/admin-data-job.entity';
import { AdminComplianceService } from './admin-compliance.service';
import { AdminComplianceController } from './admin-compliance.controller';
import { AdminDataJobsService } from './admin-data-jobs.service';
import { AdminDataJobsController } from './admin-data-jobs.controller';
import { AdminSearchService } from './admin-search.service';
import { AdminSearchController } from './admin-search.controller';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { UserPushDevice } from '../push/entities/user-push-device.entity';
import { AdminDeviceSessionsController } from './admin-device-sessions.controller';
import { AdminVerificationRequestEntity } from './entities/admin-verification-request.entity';
import { AdminVerificationService } from './admin-verification.service';
import { AdminVerificationController } from './admin-verification.controller';
import { AdminMergeService } from './admin-merge.service';
import { AdminMergeController } from './admin-merge.controller';
import { AdminDashboardService } from './admin-dashboard.service';
import { AdminRegistryService } from './admin-registry.service';
import { AdminRegistryController } from './admin-registry.controller';
import { MarineSpeciesEntity } from './entities/marine-species.entity';
import { AdminSupportTicketEntity } from './entities/admin-support-ticket.entity';
import { AdminCmsPageEntity } from './entities/admin-cms-page.entity';
import { AdminIntegrationEntity } from './entities/admin-integration.entity';
import { AdminSubscriptionPlanEntity } from './entities/admin-subscription-plan.entity';
import { AdminMarineSpeciesService } from './admin-marine-species.service';
import { AdminMarineSpeciesController } from './admin-marine-species.controller';
import { AdminCmsPagesService } from './admin-cms-pages.service';
import { AdminCmsPagesController } from './admin-cms-pages.controller';
import { AdminSupportTicketsService } from './admin-support-tickets.service';
import { AdminSupportTicketsController } from './admin-support-tickets.controller';
import { AdminIntegrationsService } from './admin-integrations.service';
import { AdminIntegrationsController } from './admin-integrations.controller';
import { AdminBillingPlansService } from './admin-billing-plans.service';
import { AdminBillingPlansController } from './admin-billing-plans.controller';
import { AdminAnalyticsService } from './admin-analytics.service';
import { AdminAnalyticsController } from './admin-analytics.controller';
import { AnalyticsEventEntity } from './entities/analytics-event.entity';
import { MailModule } from '../mail/mail.module';
import { PartnerAccountService } from './partner-account.service';
import { DiveSitesModule } from '../dive-sites/dive-sites.module';
import { AdminDiveSiteContributionsController } from './admin-dive-site-contributions.controller';
import { AdminOrSuperAdminGuard } from './admin-or-super-admin.guard';

@Module({
  imports: [
    DiveSitesModule,
    PushModule,
    MailModule,
    TypeOrmModule.forFeature([
      AdminAuditLogEntity,
      AdminReportEntity,
      AdminFeatureFlagEntity,
      AdminSystemSettingEntity,
      AdminNotificationCampaignEntity,
      AdminComplianceRequestEntity,
      AdminDataJobEntity,
      AdminVerificationRequestEntity,
      User,
      FeedPost,
      FeedPostComment,
      DiveLogEntity,
      DiveCenterEntity,
      ShopEntity,
      DiveSiteEntity,
      UserPushDevice,
      MarineSpeciesEntity,
      AdminSupportTicketEntity,
      AdminCmsPageEntity,
      AdminIntegrationEntity,
      AdminSubscriptionPlanEntity,
      AnalyticsEventEntity,
    ]),
  ],
  controllers: [
    AdminController,
    AdminUsersController,
    AdminAuditController,
    AdminModerationController,
    AdminReportsController,
    AdminFeatureFlagsController,
    AdminSystemSettingsController,
    AdminNotificationsController,
    AdminRolesController,
    AdminComplianceController,
    AdminDataJobsController,
    AdminSearchController,
    AdminDeviceSessionsController,
    AdminVerificationController,
    AdminMergeController,
    AdminRegistryController,
    AdminMarineSpeciesController,
    AdminCmsPagesController,
    AdminSupportTicketsController,
    AdminIntegrationsController,
    AdminBillingPlansController,
    AdminAnalyticsController,
    AdminDiveSiteContributionsController,
  ],
  providers: [
    ErrorStatsService,
    SuperAdminGuard,
    PermissionsGuard,
    DangerousActionGuard,
    AuditLogService,
    AdminUsersService,
    AdminModerationService,
    AdminReportsService,
    AdminFeatureFlagsService,
    AdminSystemSettingsService,
    AdminNotificationsService,
    AdminComplianceService,
    AdminDataJobsService,
    AdminSearchService,
    AdminVerificationService,
    PartnerAccountService,
    AdminMergeService,
    AdminDashboardService,
    AdminRegistryService,
    AdminMarineSpeciesService,
    AdminCmsPagesService,
    AdminSupportTicketsService,
    AdminIntegrationsService,
    AdminBillingPlansService,
    AdminAnalyticsService,
    AdminOrSuperAdminGuard,
    {
      provide: APP_FILTER,
      useClass: ErrorTrackingFilter,
    },
  ],
  exports: [ErrorStatsService, AuditLogService],
})
export class AdminModule {}
