import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { AdminDashboardService } from './admin-dashboard.service';
import { AdminSupportTicketEntity } from './entities/admin-support-ticket.entity';
import { MarineSpeciesEntity } from './entities/marine-species.entity';
import { AdminCmsPageEntity } from './entities/admin-cms-page.entity';
import { AdminIntegrationEntity } from './entities/admin-integration.entity';
import { AdminSubscriptionPlanEntity } from './entities/admin-subscription-plan.entity';
import { AnalyticsEventEntity } from './entities/analytics-event.entity';

@Injectable()
export class AdminAnalyticsService {
  constructor(
    private readonly dashboard: AdminDashboardService,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(AnalyticsEventEntity)
    private readonly analyticsEventsRepo: Repository<AnalyticsEventEntity>,
    @InjectRepository(AdminSupportTicketEntity)
    private readonly ticketsRepo: Repository<AdminSupportTicketEntity>,
    @InjectRepository(MarineSpeciesEntity)
    private readonly marineRepo: Repository<MarineSpeciesEntity>,
    @InjectRepository(AdminCmsPageEntity)
    private readonly cmsRepo: Repository<AdminCmsPageEntity>,
    @InjectRepository(AdminIntegrationEntity)
    private readonly intRepo: Repository<AdminIntegrationEntity>,
    @InjectRepository(AdminSubscriptionPlanEntity)
    private readonly plansRepo: Repository<AdminSubscriptionPlanEntity>,
  ) {}

  async listStoredEvents(params: { limit?: number; offset?: number; name?: string }) {
    const limit = Math.min(Math.max(params.limit ?? 50, 1), 200);
    const offset = Math.max(params.offset ?? 0, 0);
    const qb = this.analyticsEventsRepo
      .createQueryBuilder('e')
      .orderBy('e.createdAt', 'DESC')
      .skip(offset)
      .take(limit);
    if (params.name?.trim()) {
      qb.andWhere('e.name = :name', { name: params.name.trim() });
    }
    const [items, total] = await qb.getManyAndCount();
    return { items, total, limit, offset };
  }

  async summary() {
    const overview = await this.dashboard.getOverview();

    const [
      ticketsOpen,
      marineSpeciesTotal,
      cmsPublished,
      integrationsEnabled,
      plansActive,
      tierRows,
    ] = await Promise.all([
      this.ticketsRepo.count({
        where: {
          status: In(['open', 'pending', 'in_progress']),
        },
      }),
      this.marineRepo.count(),
      this.cmsRepo.count({ where: { status: 'published' } }),
      this.intRepo.count({ where: { enabled: true } }),
      this.plansRepo.count({ where: { active: true } }),
      this.usersRepo
        .createQueryBuilder('u')
        .select('COALESCE(u.subscriptionTier, \'\')', 'tier')
        .addSelect('COUNT(*)', 'count')
        .groupBy('u.subscriptionTier')
        .getRawMany<{ tier: string; count: string }>(),
    ]);

    return {
      generatedAt: new Date().toISOString(),
      overview,
      tzModules: {
        supportTicketsOpen: ticketsOpen,
        marineSpeciesTotal,
        cmsPagesPublished: cmsPublished,
        integrationsEnabled,
        subscriptionPlansActive: plansActive,
      },
      usersBySubscriptionTier: tierRows.map((r) => ({
        tier: r.tier || '(none)',
        count: parseInt(r.count, 10),
      })),
    };
  }
}
