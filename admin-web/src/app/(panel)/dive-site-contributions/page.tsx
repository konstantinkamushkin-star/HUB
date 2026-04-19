import { Suspense } from "react";

import { DiveSiteContributionsClient } from "./DiveSiteContributionsClient";

/** Не кэшировать HTML оболочку страницы на CDN — иначе после деплоя WKWebView может долго показывать старый бандл. */
export const dynamic = "force-dynamic";
export const revalidate = 0;

function ContributionsFallback() {
  return (
    <div className="space-y-4">
      <div className="h-8 w-72 animate-pulse rounded-lg bg-zinc-800" />
      <div className="h-32 w-full animate-pulse rounded-xl bg-zinc-900" />
      <p className="text-sm text-zinc-500">Загрузка…</p>
    </div>
  );
}

export default function DiveSiteContributionsPage() {
  return (
    <Suspense fallback={<ContributionsFallback />}>
      <DiveSiteContributionsClient />
    </Suspense>
  );
}
