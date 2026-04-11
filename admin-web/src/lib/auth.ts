const TOKEN_KEY = "divehub_admin_token";
const REFRESH_KEY = "divehub_admin_refresh";
const USER_KEY = "divehub_admin_user";

export type StoredAdminUser = {
  id: string;
  email: string;
  role: string;
  firstName?: string;
  lastName?: string;
};

const PANEL_ROLES = new Set([
  "SUPER_ADMIN",
  "ADMIN",
  "MODERATOR",
  "SUPPORT",
  "CONTENT_MANAGER",
  "FINANCE_MANAGER",
]);

export function canAccessAdminPanel(role?: string): boolean {
  if (!role) return false;
  return PANEL_ROLES.has(role);
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function getStoredUser(): StoredAdminUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAdminUser;
  } catch {
    return null;
  }
}

export function setSession(
  accessToken: string,
  user: StoredAdminUser,
  refreshToken?: string | null,
): void {
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  if (refreshToken) {
    localStorage.setItem(REFRESH_KEY, refreshToken);
  } else {
    localStorage.removeItem(REFRESH_KEY);
  }
}

/** После POST /auth/refresh — обновить access (и refresh, если сервер прислал новый). */
export function applyRefreshedTokens(payload: {
  accessToken: string;
  refreshToken?: string;
}): void {
  localStorage.setItem(TOKEN_KEY, payload.accessToken);
  if (payload.refreshToken) {
    localStorage.setItem(REFRESH_KEY, payload.refreshToken);
  }
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}
