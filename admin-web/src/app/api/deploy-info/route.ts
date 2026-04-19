import { readFile } from "node:fs/promises";
import { join } from "node:path";

/**
 * Диагностика деплоя: откройте в браузере `/api/deploy-info` на том же origin, что и панель.
 * Должны совпадать `cwd` с каталогом, откуда запускают `npm run start`, и `gitSha` с последним коммитом.
 */
export async function GET() {
  let buildId = "unknown";
  try {
    buildId = (await readFile(join(process.cwd(), ".next", "BUILD_ID"), "utf8")).trim();
  } catch {
    // dev or missing .next
  }
  return Response.json({
    ok: true,
    cwd: process.cwd(),
    buildId,
    node: process.version,
    gitSha: process.env.NEXT_PUBLIC_GIT_SHA ?? null,
  });
}
