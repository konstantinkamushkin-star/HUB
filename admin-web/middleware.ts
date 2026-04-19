import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

/**
 * Не кэшировать HTML-документы в браузере/CDN: после деплоя иначе часто остаётся старая оболочка
 * со ссылками на несуществующие чанки → «белый экран».
 * Статика `/_next/static/*` не трогаем.
 */
export function middleware(request: NextRequest) {
  const res = NextResponse.next();
  const p = request.nextUrl.pathname;
  if (request.method !== "GET") return res;
  if (p.startsWith("/_next/static") || p.startsWith("/_next/image")) return res;
  if (/\.(ico|png|jpg|jpeg|gif|svg|webp|txt|xml|json|woff2?)$/i.test(p)) return res;
  res.headers.set(
    "Cache-Control",
    "no-store, no-cache, must-revalidate, max-age=0",
  );
  res.headers.set("Pragma", "no-cache");
  return res;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image).*)"],
};
