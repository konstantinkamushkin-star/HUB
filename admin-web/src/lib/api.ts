import { ADMIN_LOGIN_PATH } from "./adminLoginPath";
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

/** Сообщение для человека: по-русски, без кодов HTTP и без «(HTTP 401)» из Nest. */
function userFacingApiErrorMessage(status: number, raw: string | null): string {
  const stripHttp = (s: string) =>
    s
      .replace(/\s*\(HTTP\s*\d+\)\s*$/i, "")
      .replace(/^HTTP\s*\d+\s*[–—:\-]\s*/i, "")
      .trim();

  const cleaned = stripHttp((raw ?? "").trim());
  const lower = cleaned.toLowerCase();

  if (
    lower.includes("invalid email") ||
    lower.includes("invalid password") ||
    lower.includes("invalid credentials")
  ) {
    return "Неверный email или пароль";
  }
  if (lower.includes("unauthorized") && cleaned.length < 80) {
    return "Нужно войти в систему";
  }
  if (lower.includes("forbidden") || lower.includes("not allowed")) {
    return "Недостаточно прав для этого действия";
  }
  if (lower.includes("not found")) {
    return "Данные не найдены";
  }
  if (lower.includes("too many requests") || lower.includes("throttl")) {
    return "Слишком много запросов. Подождите немного и повторите.";
  }

  if (cleaned.length > 0 && !/\bHTTP\s*\d+\b/i.test(cleaned)) {
    return cleaned;
  }

  if (status === 400) return "Некорректный запрос";
  if (status === 401) return "Неверный email или пароль";
  if (status === 403) return "Недостаточно прав для этого действия";
  if (status === 404) return "Данные не найдены";
  if (status === 409) return "Конфликт данных. Обновите страницу и попробуйте снова.";
  if (status === 429) return "Слишком много запросов. Подождите немного и повторите.";
  if (status >= 500) return "Ошибка на сервере. Попробуйте позже.";
  if (status === 0) {
    return "Нет подключения к интернету. Проверьте сеть и попробуйте снова.";
  }
  return "Операция не выполнена";
}

function isLikelyNoInternet(err: unknown): boolean {
  if (!(err instanceof Error)) return false;
  const m = (err.message || "").toLowerCase();
  if (err.name === "TypeError") {
    return (
      m.includes("failed to fetch") ||
      m.includes("load failed") ||
      m.includes("networkerror") ||
      m.includes("fetch failed")
    );
  }
  return false;
}

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
    let raw: string | null = null;
    if (data && typeof data === "object" && "message" in data) {
      const m = (data as { message?: unknown }).message;
      raw =
        typeof m === "string"
          ? m
          : Array.isArray(m)
            ? m.join(", ")
            : res.statusText || null;
    } else if (typeof data === "string" && data.length > 0 && data.length < 500) {
      raw = data;
    } else {
      raw = res.statusText || null;
    }
    errorMessage = userFacingApiErrorMessage(res.status, raw);
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
    if (isLikelyNoInternet(e)) {
      return {
        ok: false,
        status: 0,
        data: null,
        errorMessage:
          "Нет подключения к интернету. Проверьте Wi‑Fi или кабель и попробуйте снова.",
      };
    }
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
    window.location.assign(ADMIN_LOGIN_PATH);
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
