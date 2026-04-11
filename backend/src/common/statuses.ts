export enum UserAccountStatus {
  ACTIVE = 'active',
  PENDING = 'pending',
  SUSPENDED = 'suspended',
  BANNED = 'banned',
  DELETED = 'deleted',
  MERGED = 'merged',
}

export enum VerificationStatus {
  UNVERIFIED = 'unverified',
  PENDING = 'pending',
  VERIFIED = 'verified',
  REVOKED = 'revoked',
  REJECTED = 'rejected',
}

export enum RiskLevel {
  LOW = 'low',
  NORMAL = 'normal',
  HIGH = 'high',
  CRITICAL = 'critical',
}

export enum DiveCenterStatus {
  DRAFT = 'draft',
  PENDING = 'pending',
  VERIFIED = 'verified',
  REJECTED = 'rejected',
  SUSPENDED = 'suspended',
  MERGED = 'merged',
  DUPLICATE = 'duplicate',
}

export enum DiveSiteStatus {
  DRAFT = 'draft',
  PENDING = 'pending',
  PUBLISHED = 'published',
  HIDDEN = 'hidden',
  DUPLICATE = 'duplicate',
  MERGED = 'merged',
  ARCHIVED = 'archived',
}

export enum FeedPostStatus {
  DRAFT = 'draft',
  PUBLISHED = 'published',
  HIDDEN = 'hidden',
  REMOVED = 'removed',
  REPORTED = 'reported',
  UNDER_REVIEW = 'under_review',
}

export enum DiveLogModerationStatus {
  DRAFT = 'draft',
  PENDING = 'pending',
  PUBLISHED = 'published',
  HIDDEN = 'hidden',
  REPORTED = 'reported',
  UNDER_REVIEW = 'under_review',
  REMOVED = 'removed',
  RESTORED = 'restored',
}

export enum ReportStatus {
  NEW = 'new',
  IN_REVIEW = 'in_review',
  RESOLVED = 'resolved',
  REJECTED = 'rejected',
  ESCALATED = 'escalated',
}

export enum ReportPriority {
  LOW = 'low',
  NORMAL = 'normal',
  HIGH = 'high',
  CRITICAL = 'critical',
}
