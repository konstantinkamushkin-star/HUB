import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../users/entities/user.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import { DiveSiteEntity } from '../dive-sites/entities/dive-site.entity';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { AdminReportEntity } from './entities/admin-report.entity';

@Injectable()
export class AdminSearchService {
  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    @InjectRepository(DiveSiteEntity)
    private readonly sitesRepo: Repository<DiveSiteEntity>,
    @InjectRepository(FeedPost) private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    @InjectRepository(AdminReportEntity)
    private readonly reportsRepo: Repository<AdminReportEntity>,
  ) {}

  async globalSearch(query: string, limit = 10) {
    const q = query.trim();
    if (q.length < 2) return { users: [], centers: [], sites: [], posts: [], logs: [], reports: [] };
    const lim = Math.min(Math.max(limit, 1), 50);
    const pattern = `%${q}%`;

    const [users, centers, sites, posts, logs, reports] = await Promise.all([
      this.usersRepo
        .createQueryBuilder('u')
        .where('LOWER(u.email) LIKE LOWER(:p) OR LOWER(u."firstName") LIKE LOWER(:p) OR LOWER(u."lastName") LIKE LOWER(:p) OR u.id::text = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
      this.centersRepo
        .createQueryBuilder('c')
        .where('LOWER(c.name) LIKE LOWER(:p) OR c.id::text = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
      this.sitesRepo
        .createQueryBuilder('s')
        .where('LOWER(s.name) LIKE LOWER(:p) OR s.id::text = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
      this.postsRepo
        .createQueryBuilder('p')
        .where('LOWER(COALESCE(p.content, \'\')) LIKE LOWER(:p) OR p.id::text = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
      this.logsRepo
        .createQueryBuilder('l')
        .where('LOWER(COALESCE(l.notes, \'\')) LIKE LOWER(:p) OR l.id::text = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
      this.reportsRepo
        .createQueryBuilder('r')
        .where('LOWER(COALESCE(r.message, \'\')) LIKE LOWER(:p) OR r.id::text = :id OR r."targetId" = :id', { p: pattern, id: q })
        .take(lim)
        .getMany(),
    ]);

    return { users, centers, sites, posts, logs, reports };
  }
}
