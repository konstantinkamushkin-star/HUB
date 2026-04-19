import Link from "next/link";

import { JsonPanel } from "@/components/panel/JsonPanel";

export default function DiveSitesPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-sky-500/30 bg-sky-950/25 px-4 py-3 text-sm text-zinc-200">
        <strong className="text-zinc-100">Заявки пользователей</strong> — предложить{" "}
        <span className="text-zinc-300">новый</span> дайв-сайт или{" "}
        <span className="text-zinc-300">исправление</span> карточки — в разделе{" "}
        <Link
          href="/dive-site-contributions"
          className="font-medium text-sky-400 underline decoration-sky-500/50 underline-offset-2 hover:text-sky-300"
        >
          Заявки на дайв-сайты
        </Link>
        . Здесь ниже — только справочник опубликованных точек (registry).
      </div>
      <JsonPanel
        title="Дайв-сайты"
        description="Параметры: limit, offset, status, query. Право: verify:entities."
        apiPath="/admin/registry/dive-sites?limit=50"
      />
    </div>
  );
}
