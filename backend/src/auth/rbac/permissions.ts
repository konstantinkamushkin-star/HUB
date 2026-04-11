import { AdminRole } from './admin-roles';

export enum Permission {
  VIEW_ADMIN_DASHBOARD = 'view:admin_dashboard',
  VIEW_ERROR_STATS = 'view:error_stats',
  MANAGE_USERS = 'manage:users',
  MODERATE_CONTENT = 'moderate:content',
  MANAGE_SETTINGS = 'manage:settings',
  VIEW_AUDIT_LOGS = 'view:audit_logs',
  MANAGE_ROLES = 'manage:roles',
  VERIFY_ENTITIES = 'verify:entities',
  MERGE_ENTITIES = 'merge:entities',
  MANAGE_MARINE_LIFE = 'manage:marine_life',
  MANAGE_CMS = 'manage:cms',
  MANAGE_SUPPORT = 'manage:support',
  MANAGE_BILLING = 'manage:billing',
  MANAGE_INTEGRATIONS = 'manage:integrations',
}

const SUPER_ADMIN_PERMISSIONS = Object.values(Permission);

const ADMIN_BASE_PERMISSIONS: Permission[] = [
  Permission.VIEW_ADMIN_DASHBOARD,
  Permission.VIEW_ERROR_STATS,
  Permission.MANAGE_USERS,
  Permission.MODERATE_CONTENT,
  Permission.VIEW_AUDIT_LOGS,
  Permission.VERIFY_ENTITIES,
  Permission.MERGE_ENTITIES,
  Permission.MANAGE_MARINE_LIFE,
  Permission.MANAGE_CMS,
  Permission.MANAGE_SUPPORT,
  Permission.MANAGE_BILLING,
  Permission.MANAGE_INTEGRATIONS,
];

const MODERATOR_PERMISSIONS: Permission[] = [
  Permission.VIEW_ADMIN_DASHBOARD,
  Permission.MODERATE_CONTENT,
  Permission.VIEW_AUDIT_LOGS,
  Permission.VERIFY_ENTITIES,
  Permission.MERGE_ENTITIES,
];

const SUPPORT_PERMISSIONS: Permission[] = [
  Permission.VIEW_ADMIN_DASHBOARD,
  Permission.MANAGE_USERS,
  Permission.MANAGE_SUPPORT,
];

const CONTENT_MANAGER_PERMISSIONS: Permission[] = [
  Permission.VIEW_ADMIN_DASHBOARD,
  Permission.MODERATE_CONTENT,
  Permission.MANAGE_MARINE_LIFE,
  Permission.MANAGE_CMS,
];

const FINANCE_MANAGER_PERMISSIONS: Permission[] = [
  Permission.VIEW_ADMIN_DASHBOARD,
  Permission.VIEW_AUDIT_LOGS,
  Permission.MANAGE_BILLING,
];

export const ROLE_PERMISSIONS: Record<AdminRole, Permission[]> = {
  [AdminRole.SUPER_ADMIN]: SUPER_ADMIN_PERMISSIONS,
  [AdminRole.ADMIN]: ADMIN_BASE_PERMISSIONS,
  [AdminRole.MODERATOR]: MODERATOR_PERMISSIONS,
  [AdminRole.SUPPORT]: SUPPORT_PERMISSIONS,
  [AdminRole.CONTENT_MANAGER]: CONTENT_MANAGER_PERMISSIONS,
  [AdminRole.FINANCE_MANAGER]: FINANCE_MANAGER_PERMISSIONS,
};
