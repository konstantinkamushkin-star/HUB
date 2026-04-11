import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { AdminReportEntity } from './entities/admin-report.entity';
import { AdminComplianceRequestEntity } from './entities/admin-compliance-request.entity';
import { AdminVerificationRequestEntity } from './entities/admin-verification-request.entity';
import { AdminDataJobEntity } from './entities/admin-data-job.entity';
import { AdminNotificationCampaignEntity } from './entities/notification-campaign.entity';
import { MarineSpeciesEntity } from './entities/marine-species.entity';
import { AdminSupportTicketEntity } from './entities/admin-support-ticket.entity';
import { AdminCmsPageEntity } from './entities/admin-cms-page.entity';
import { AdminIntegrationEntity } from './entities/admin-integration.entity';
import { AdminSubscriptionPlanEntity } from './entities/admin-subscription-plan.entity';
import { ErrorStatsService } from './error-stats.service';
import { ReportStatus, VerificationStatus } from '../common/statuses';

@Injectable()
export class AdminDashboardService {
  constructor(
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(FeedPost)
    private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(FeedPostComment)
    private readonly commentsRepo: Repository<FeedPostComment>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly sitesRepo: Repository<DiveSiteEntity>,
    @InjectRepository(AdminReportEntity)
    private readonly reportsRepo: Repository<AdminReportEntity>,
    @InjectRepository(AdminComplianceRequestEntity)
    private readonly complianceRepo: Repository<AdminComplianceRequestEntity>,
    @InjectRepository(AdminVerificationRequestEntity)
    private readonly verificationRepo: Repository<AdminVerificationRequestEntity>,
    @InjectRepository(AdminDataJobEntity)
    private readonly dataJobsRepo: Repository<AdminDataJobEntity>,
    @InjectRepository(AdminNotificationCampaignEntity)
    private readonly campaignsRepo: Repository<AdminNotificationCampaignEntity>,
    @InjectRepository(MarineSpeciesEntity)
    private readonly marineRepo: Repository<MarineSpeciesEntity>,
    @InjectRepository(AdminSupportTicketEntity)
    private readonly ticketsRepo: Repository<AdminSupportTicketEntity>,
    @InjectRepository(AdminCmsPageEntity)
    private readonly cmsRepo: Repository<AdminCmsPageEntity>,
    @InjectRepository(AdminIntegrationEntity)
    private readonly integrationsRepo: Repository<AdminIntegrationEntity>,
    @InjectRepository(AdminSubscriptionPlanEntity)
    private readonly plansRepo: Repository<AdminSubscriptionPlanEntity>,
    private readonly errorStatsService: ErrorStatsService,
  ) {}

  async getOverview() {
    const now = Date.now();
    const d1 = new Date(now - 24 * 60 * 60 * 1000);
    const d7 = new Date(now - 7 * 24 * 60 * 60 * 1000);
    const d30 = new Date(now - 30 * 24 * 60 * 60 * 1000);

    const [
      usersTotal,
      usersNew24h,
      usersNew7d,
      usersNew30d,
      postsTotal,
      postsNew7d,
      commentsTotal,
      diveLogsTotal,
      diveLogsNew7d,
      centersTotal,
      centersVerified,
      sitesTotal,
      reportsTotal,
      reportsNew24h,
      reportsOpen,
      compliancePending,
      verificationPending,
      jobsQueued,
      campaignsTotal,
      marineSpeciesTotal,
      supportTicketsOpen,
      cmsPublished,
      integrationsOn,
      plansActive,
    ] = await Promise.all([
      this.usersRepo.count(),
      this.usersRepo
        .createQueryBuilder('u')
        .where('u.createdAt > :d', { d: d1 })
        .getCount(),
      this.usersRepo
        .createQueryBuilder('u')
        .where('u.createdAt > :d', { d: d7 })
        .getCount(),
      this.usersRepo
        .createQueryBuilder('u')
        .where('u.createdAt > :d', { d: d30 })
        .getCount(),
      this.postsRepo.count(),
      this.postsRepo
        .createQueryBuilder('p')
        .where('p.createdAt > :d', { d: d7 })
        .getCount(),
      this.commentsRepo.count(),
      this.logsRepo.count(),
      this.logsRepo
        .createQueryBuilder('l')
        .where('l.createdAt > :d', { d: d7 })
        .getCount(),
      this.centersRepo.count(),
      this.centersRepo.count({
        where: { verification_status: VerificationStatus.VERIFIED },
      }),
      this.sitesRepo.count(),
      this.reportsRepo.count(),
      this.reportsRepo
        .createQueryBuilder('r')
        .where('r.createdAt > :d', { d: d1 })
        .getCount(),
      this.reportsRepo.count({
        where: { status: In([ReportStatus.NEW, ReportStatus.IN_REVIEW]) },
      }),
      this.complianceRepo.count({ where: { status: 'pending' } }),
      this.verificationRepo.count({ where: { status: 'pending' } }),
      this.dataJobsRepo.count({ where: { status: 'queued' } }),
      this.campaignsRepo.count(),
      this.marineRepo.count(),
      this.ticketsRepo.count({
        where: {
          status: In(['open', 'pending', 'in_progress']),
        },
      }),
      this.cmsRepo.count({ where: { status: 'published' } }),
      this.integrationsRepo.count({ where: { enabled: true } }),
      this.plansRepo.count({ where: { active: true } }),
    ]);

    const errorStats = this.errorStatsService.getStats();

    return {
      generatedAt: new Date().toISOString(),
      counts: {
        users: usersTotal,
        usersNewLast24h: usersNew24h,
        usersNewLast7d: usersNew7d,
        usersNewLast30d: usersNew30d,
        feedPosts: postsTotal,
        feedPostsNewLast7d: postsNew7d,
        feedComments: commentsTotal,
        diveLogs: diveLogsTotal,
        diveLogsNewLast7d: diveLogsNew7d,
        diveCenters: centersTotal,
        diveCentersVerified: centersVerified,
        diveSites: sitesTotal,
        reports: reportsTotal,
        reportsNewLast24h: reportsNew24h,
        reportsOpenQueue: reportsOpen,
        complianceRequestsPending: compliancePending,
        verificationRequestsPending: verificationPending,
        dataJobsQueued: jobsQueued,
        notificationCampaigns: campaignsTotal,
        marineSpecies: marineSpeciesTotal,
        supportTicketsOpen,
        cmsPagesPublished: cmsPublished,
        integrationsEnabled: integrationsOn,
        subscriptionPlansActive: plansActive,
      },
      systemHealth: errorStats,
    };
  }
}
