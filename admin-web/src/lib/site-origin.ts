/**
 * Базовый URL публичного сайта (документы, ссылки в согласиях).
 * В проде задайте NEXT_PUBLIC_SITE_URL, например https://dive-hub.ru
 */
export function getSiteOrigin(): string {
  const fromEnv = process.env.NEXT_PUBLIC_SITE_URL?.trim().replace(/\/$/, "");
  if (fromEnv) return fromEnv;
  if (typeof window !== "undefined") return window.location.origin;
  return "";
}
