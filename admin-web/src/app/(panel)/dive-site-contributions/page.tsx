import { Suspense } from "react";

import { DiveSiteContributionsClient } from "./DiveSiteContributionsClient";

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
