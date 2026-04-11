import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { FeedPost } from '../feed/entities/feed-post.entity';
import { FeedPostComment } from '../feed/entities/feed-post-comment.entity';
import { DiveLogEntity } from '../dive-logs/entities/dive-log.entity';
import { DiveCenterEntity } from '../dive-centers/entities/dive-center.entity';
import {
  DiveCenterStatus,
  DiveLogModerationStatus,
  FeedPostStatus,
  VerificationStatus,
} from '../common/statuses';
import { AuditLogService } from './audit-log.service';
import { ModerationActionDto } from './dto/moderation-action.dto';

@Injectable()
export class AdminModerationService {
  constructor(
    @InjectRepository(FeedPost)
    private readonly postsRepo: Repository<FeedPost>,
    @InjectRepository(FeedPostComment)
    private readonly commentsRepo: Repository<FeedPostComment>,
    @InjectRepository(DiveLogEntity)
    private readonly logsRepo: Repository<DiveLogEntity>,
    @InjectRepository(DiveCenterEntity)
    private readonly centersRepo: Repository<DiveCenterEntity>,
    private readonly auditLogService: AuditLogService,
  ) {}

  private async audit(actor: any, action: string, targetType: string, targetId: string, before: any, after: any, reason?: string) {
    await this.auditLogService.write({
      adminId: actor?.adminId ?? null,
      action,
      targetType,
      targetId,
      before,
      after,
      reason: reason ?? null,
      ip: actor?.ip ?? null,
      device: actor?.userAgent ?? null,
      correlationId: actor?.correlationId ?? null,
    });
  }

  async hidePost(postId: string, dto: ModerationActionDto, actor: any) {
    const post = await this.postsRepo.findOne({ where: { id: postId } });
    if (!post) throw new NotFoundException('Post not found');
    const before = { moderationStatus: post.moderationStatus };
    post.moderationStatus = FeedPostStatus.HIDDEN;
    const saved = await this.postsRepo.save(post);
    await this.audit(actor, 'admin.post.hide', 'feed_post', postId, before, { moderationStatus: saved.moderationStatus }, dto.reason);
    return saved;
  }

  async restorePost(postId: string, dto: ModerationActionDto, actor: any) {
    const post = await this.postsRepo.findOne({ where: { id: postId } });
    if (!post) throw new NotFoundException('Post not found');
    const before = { moderationStatus: post.moderationStatus };
    post.moderationStatus = FeedPostStatus.PUBLISHED;
    const saved = await this.postsRepo.save(post);
    await this.audit(actor, 'admin.post.restore', 'feed_post', postId, before, { moderationStatus: saved.moderationStatus }, dto.reason);
    return saved;
  }

  async hideComment(commentId: string, dto: ModerationActionDto, actor: any) {
    const comment = await this.commentsRepo.findOne({ where: { id: commentId } });
    if (!comment) throw new NotFoundException('Comment not found');
    const before = { moderationStatus: comment.moderationStatus };
    comment.moderationStatus = FeedPostStatus.HIDDEN;
    comment.deletedAt = new Date();
    const saved = await this.commentsRepo.save(comment);
    await this.audit(actor, 'admin.comment.hide', 'feed_comment', commentId, before, { moderationStatus: saved.moderationStatus }, dto.reason);
    return saved;
  }

  async restoreComment(commentId: string, dto: ModerationActionDto, actor: any) {
    const comment = await this.commentsRepo.findOne({ where: { id: commentId } });
    if (!comment) throw new NotFoundException('Comment not found');
    const before = { moderationStatus: comment.moderationStatus, deletedAt: comment.deletedAt };
    comment.moderationStatus = FeedPostStatus.PUBLISHED;
    comment.deletedAt = null;
    const saved = await this.commentsRepo.save(comment);
    await this.audit(actor, 'admin.comment.restore', 'feed_comment', commentId, before, { moderationStatus: saved.moderationStatus, deletedAt: saved.deletedAt }, dto.reason);
    return saved;
  }

  async hideDiveLog(logId: string, dto: ModerationActionDto, actor: any) {
    const log = await this.logsRepo.findOne({ where: { id: logId } });
    if (!log) throw new NotFoundException('Dive log not found');
    const before = { moderationStatus: log.moderationStatus };
    log.moderationStatus = DiveLogModerationStatus.HIDDEN;
    log.deletedAt = new Date();
    const saved = await this.logsRepo.save(log);
    await this.audit(actor, 'admin.dive_log.hide', 'dive_log', logId, before, { moderationStatus: saved.moderationStatus }, dto.reason);
    return saved;
  }

  async restoreDiveLog(logId: string, dto: ModerationActionDto, actor: any) {
    const log = await this.logsRepo.findOne({ where: { id: logId } });
    if (!log) throw new NotFoundException('Dive log not found');
    const before = { moderationStatus: log.moderationStatus, deletedAt: log.deletedAt };
    log.moderationStatus = DiveLogModerationStatus.RESTORED;
    log.deletedAt = null;
    const saved = await this.logsRepo.save(log);
    await this.audit(actor, 'admin.dive_log.restore', 'dive_log', logId, before, { moderationStatus: saved.moderationStatus, deletedAt: saved.deletedAt }, dto.reason);
    return saved;
  }

  async verifyCenter(centerId: string, dto: ModerationActionDto, actor: any) {
    const center = await this.centersRepo.findOne({ where: { id: centerId } });
    if (!center) throw new NotFoundException('Dive center not found');
    const before = { status: center.status, verification_status: center.verification_status };
    center.status = DiveCenterStatus.VERIFIED;
    center.verification_status = VerificationStatus.VERIFIED;
    const saved = await this.centersRepo.save(center);
    await this.audit(actor, 'admin.center.verify', 'dive_center', centerId, before, { status: saved.status, verification_status: saved.verification_status }, dto.reason);
    return saved;
  }

  async rejectCenter(centerId: string, dto: ModerationActionDto, actor: any) {
    const center = await this.centersRepo.findOne({ where: { id: centerId } });
    if (!center) throw new NotFoundException('Dive center not found');
    const before = { status: center.status, verification_status: center.verification_status };
    center.status = DiveCenterStatus.REJECTED;
    center.verification_status = VerificationStatus.REJECTED;
    const saved = await this.centersRepo.save(center);
    await this.audit(actor, 'admin.center.reject', 'dive_center', centerId, before, { status: saved.status, verification_status: saved.verification_status }, dto.reason);
    return saved;
  }
}
