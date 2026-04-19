import { readFile } from "node:fs/promises";
import { join } from "node:path";

/** Same resolution as `api-proxy/[...path]/route.ts` — must match for diagnostics. */
function backendOrigin(): string {
  const raw =
    process.env.BACKEND_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    "https://api.dive-hub.ru";
  return raw.replace(/\/$/, "");
}

/**
 * Диагностика деплоя: откройте в браузере `/api/deploy-info` на том же origin, что и панель.
 * Должны совпадать `cwd` с каталогом, откуда запускают `npm run start`, и `gitSha` с последним коммитом.
 *
 * `backendProbe`: без токена GET `/api/admin/dive-site-contributions` обычно даёт **401** (маршрут есть).
 * Если **404** — на `resolvedOrigin` крутится старый Nest или неверный `BACKEND_URL`.
 */
export async function GET() {
  let buildId = "unknown";
  try {
    buildId = (await readFile(join(process.cwd(), ".next", "BUILD_ID"), "utf8")).trim();
  } catch {
    // dev or missing .next
  }

  const resolvedOrigin = backendOrigin();
  let backendProbe: {
    resolvedOrigin: string;
    healthStatus: number | null;
    adminDiveSiteContributionsListStatus: number | null;
    supportChatNoAuthStatus: number | null;
    fetchError: string | null;
  } = {
    resolvedOrigin,
    healthStatus: null,
    adminDiveSiteContributionsListStatus: null,
    supportChatNoAuthStatus: null,
    fetchError: null,
  };

  try {
    const [healthRes, listRes, chatRes] = await Promise.all([
      fetch(`${resolvedOrigin}/api/health`, { cache: "no-store" }),
      fetch(`${resolvedOrigin}/api/admin/dive-site-contributions`, { cache: "no-store" }),
      fetch(
        `${resolvedOrigin}/api/admin/dive-site-contributions/support-chat?contributionId=00000000-0000-0000-0000-000000000000`,
        { cache: "no-store" },
      ),
    ]);
    backendProbe = {
      ...backendProbe,
      healthStatus: healthRes.status,
      adminDiveSiteContributionsListStatus: listRes.status,
      supportChatNoAuthStatus: chatRes.status,
    };
  } catch (e) {
    backendProbe.fetchError = e instanceof Error ? e.message : String(e);
  }

  return Response.json({
    ok: true,
    cwd: process.cwd(),
    buildId,
    node: process.version,
    gitSha: process.env.NEXT_PUBLIC_GIT_SHA ?? null,
    envBackendUrl: process.env.BACKEND_URL ?? null,
    envNextPublicApiUrl: process.env.NEXT_PUBLIC_API_URL ?? null,
    backendProbe,
  });
}
