export enum AdminRole {
  SUPER_ADMIN = 'SUPER_ADMIN',
  ADMIN = 'ADMIN',
  MODERATOR = 'MODERATOR',
  SUPPORT = 'SUPPORT',
  CONTENT_MANAGER = 'CONTENT_MANAGER',
  FINANCE_MANAGER = 'FINANCE_MANAGER',
}

export const ALL_ADMIN_ROLES: AdminRole[] = [
  AdminRole.SUPER_ADMIN,
  AdminRole.ADMIN,
  AdminRole.MODERATOR,
  AdminRole.SUPPORT,
  AdminRole.CONTENT_MANAGER,
  AdminRole.FINANCE_MANAGER,
];

export function isAdminRole(role?: string): role is AdminRole {
  if (!role) return false;
  return ALL_ADMIN_ROLES.includes(role as AdminRole);
}
