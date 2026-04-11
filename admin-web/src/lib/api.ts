import { getApiBaseUrl } from "./config";
import {
  applyRefreshedTokens,
  clearSession,
  getRefreshToken,
  getToken,
  type StoredAdminUser,
} from "./auth";

export type ApiResult<T = unknown> = {
  ok: boolean;
  status: number;
  data: T | null;
  errorMessage: string | null;
};

/**
 * В браузере ходим на тот же origin (`/api-proxy/...`) — Next переписывает запрос на Nest.
 * Так Safari не делает cross-origin fetch на другой порт (меньше «Load failed» / CORS).
 */
function buildUrl(apiPath: string): string {
  const path = apiPath.startsWith("/") ? apiPath : `/${apiPath}`;
  const tail = path.startsWith("/") ? path.slice(1) : path;
  if (typeof window !== "undefined") {
    return `/api-proxy/${tail}`;
  }
  const base = getApiBaseUrl();
  return `${base}/api${path}`;
}

let refreshInFlight: Promise<boolean> | null = null;

async function tryRefreshAccessToken(): Promise<boolean> {
  if (typeof window === "undefined") return false;
  const rt = getRefreshToken();
  if (!rt) return false;

  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(buildUrl("/auth/refresh"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: rt }),
        });
        const text = await res.text();
        let body: unknown = null;
        if (text) {
          try {
            body = JSON.parse(text) as unknown;
          } catch {
            body = text;
          }
        }
        if (!res.ok || !body || typeof body !== "object") {
          clearSession();
          return false;
        }
        const o = body as {
          accessToken?: string;
          refreshToken?: string;
        };
        if (!o.accessToken) {
          clearSession();
          return false;
        }
        applyRefreshedTokens({
          accessToken: o.accessToken,
          refreshToken: o.refreshToken,
        });
        return true;
      } catch {
        clearSession();
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }

  return refreshInFlight!;
}

function parseApiResult<T>(res: Response, text: string): ApiResult<T> {
  let data: T | null = null;
  if (text) {
    try {
      data = JSON.parse(text) as T;
    } catch {
      data = text as unknown as T;
    }
  }

  let errorMessage: string | null = null;
  if (!res.ok) {
    if (data && typeof data === "object" && "message" in data) {
      const m = (data as { message?: unknown }).message;
      errorMessage =
        typeof m === "string"
          ? m
          : Array.isArray(m)
            ? m.join(", ")
            : res.statusText;
    } else if (typeof data === "string" && data.length > 0 && data.length < 500) {
      errorMessage = data;
    } else {
      errorMessage = res.statusText || `HTTP ${res.status}`;
    }
  }

  return { ok: res.ok, status: res.status, data, errorMessage };
}

export async function apiRequest<T = unknown>(
  apiPath: string,
  init?: RequestInit,
  didTryRefresh = false,
): Promise<ApiResult<T>> {
  const headers = new Headers(init?.headers);
  const token = getToken();
  const hadToken = Boolean(token);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(buildUrl(apiPath), { ...init, headers });
  } catch (e) {
    const hint =
      typeof window !== "undefined"
        ? " Нет ответа от API: запущен ли backend (npm run start:dev в папке backend)?"
        : "";
    const msg =
      e instanceof TypeError
        ? `${e.message}.${hint}`
        : e instanceof Error
          ? e.message
          : "Ошибка сети";
    return { ok: false, status: 0, data: null, errorMessage: msg };
  }

  const text = await res.text();

  if (
    res.status === 401 &&
    hadToken &&
    !didTryRefresh &&
    typeof window !== "undefined" &&
    apiPath !== "/auth/refresh" &&
    apiPath !== "/auth/login"
  ) {
    const okRefresh = await tryRefreshAccessToken();
    if (okRefresh) {
      return apiRequest<T>(apiPath, init, true);
    }
    clearSession();
    window.location.assign("/login");
    return {
      ok: false,
      status: 401,
      data: null,
      errorMessage: "Сессия истекла — войдите снова",
    };
  }

  return parseApiResult<T>(res, text);
}

export function apiGet<T = unknown>(apiPath: string): Promise<ApiResult<T>> {
  return apiRequest<T>(apiPath, { method: "GET" });
}

export async function apiLogin(email: string, password: string) {
  return apiRequest<{
    accessToken: string;
    refreshToken: string;
    user: StoredAdminUser;
  }>("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
}
