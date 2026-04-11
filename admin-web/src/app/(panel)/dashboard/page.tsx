"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";

type Overview = {
  generatedAt?: string;
  counts?: Record<string, number>;
  systemHealth?: Record<string, unknown>;
};

const LABELS: Record<string, string> = {
  users: "Пользователи",
  usersNewLast24h: "Новых за 24 ч",
  usersNewLast7d: "Новых за 7 дн.",
  usersNewLast30d: "Новых за 30 дн.",
  feedPosts: "Посты ленты",
  feedPostsNewLast7d: "Постов за 7 дн.",
  feedComments: "Комментарии",
  diveLogs: "Дайв-логи",
  diveLogsNewLast7d: "Логов за 7 дн.",
  diveCenters: "Дайв-центры",
  diveCentersVerified: "Верифицированные центры",
  diveSites: "Дайв-сайты",
  reports: "Жалобы всего",
  reportsNewLast24h: "Жалоб за 24 ч",
  reportsOpenQueue: "Жалобы в очереди",
  complianceRequestsPending: "Compliance (ожидание)",
  verificationRequestsPending: "Верификация (ожидание)",
  dataJobsQueued: "Задачи импорта/экспорта в очереди",
  notificationCampaigns: "Кампании уведомлений",
  marineSpecies: "Виды (морская жизнь)",
  supportTicketsOpen: "Тикеты поддержки (открытые)",
  cmsPagesPublished: "CMS-страниц опубликовано",
  integrationsEnabled: "Интеграций включено",
  subscriptionPlansActive: "Тарифных планов активно",
};

export default function DashboardPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let c = false;
    (async () => {
      const res = await apiGet<Overview>("/admin/dashboard/overview");
      if (c) return;
      if (!res.ok) setErr(res.errorMessage ?? "Ошибка");
      else setData(res.data);
      setLoading(false);
    })();
    return () => {
      c = true;
    };
  }, []);

  if (loading) {
    return <p className="text-zinc-400">Загрузка дашборда…</p>;
  }
  if (err) {
    return (
      <div className="rounded-lg border border-red-900/50 bg-red-950/30 p-4 text-red-200">
        {err}
      </div>
    );
  }

  const counts = data?.counts ?? {};

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-white">Дашборд</h1>
        {data?.generatedAt ? (
          <p className="mt-1 text-sm text-zinc-500">
            Данные на {new Date(data.generatedAt).toLocaleString("ru-RU")}
          </p>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Object.entries(counts).map(([key, value]) => (
          <div
            key={key}
            className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4"
          >
            <div className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              {LABELS[key] ?? key}
            </div>
            <div className="mt-1 text-2xl font-semibold tabular-nums text-white">
              {value}
            </div>
          </div>
        ))}
      </div>

      {data?.systemHealth ? (
        <div>
          <h2 className="mb-2 text-lg font-medium text-white">
            Состояние ошибок (бекенд)
          </h2>
          <pre className="max-h-64 overflow-auto rounded-lg border border-zinc-800 bg-zinc-900/50 p-4 text-xs text-zinc-300">
            {JSON.stringify(data.systemHealth, null, 2)}
          </pre>
        </div>
      ) : null}
    </div>
  );
}
