"use client";

import { useCallback, useEffect, useState } from "react";
import { apiGet, apiRequest } from "@/lib/api";

type ContributionRow = {
  id: string;
  contribution_type: string;
  dive_site_id: string | null;
  submitter_user_id: string;
  proposed_data: Record<string, unknown>;
  message: string | null;
  status: string;
  reviewed_by: string | null;
  reviewed_at: string | null;
  rejection_reason: string | null;
  created_at: string;
  updated_at: string;
  submitterEmail?: string | null;
};

function typeLabel(t: string): string {
  if (t === "correction") return "Исправление";
  if (t === "new_site") return "Новый сайт";
  return t;
}

function statusLabel(s: string): string {
  if (s === "pending") return "Ожидает";
  if (s === "approved") return "Принято";
  if (s === "rejected") return "Отклонено";
  return s;
}

export function DiveSiteContributionsClient() {
  const [statusFilter, setStatusFilter] = useState<string>("pending");
  const [rows, setRows] = useState<ContributionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejectFor, setRejectFor] = useState<ContributionRow | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setListError(null);
    const q =
      statusFilter && statusFilter !== "all"
        ? `?status=${encodeURIComponent(statusFilter)}&limit=200`
        : "?limit=200";
    const res = await apiGet<{ success?: boolean; data?: ContributionRow[] }>(
      `/admin/dive-site-contributions${q}`,
    );
    if (!res.ok) {
      setListError(res.errorMessage || "Не удалось загрузить заявки");
      setRows([]);
    } else {
      const body = res.data;
      const list =
        body && typeof body === "object" && Array.isArray(body.data)
          ? body.data
          : [];
      setRows(list);
    }
    setLoading(false);
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const approve = async (id: string) => {
    setBusyId(id);
    setActionError(null);
    const res = await apiRequest(`/admin/dive-site-contributions/${id}/approve`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    setBusyId(null);
    if (!res.ok) {
      setActionError(res.errorMessage || "Не удалось применить");
      return;
    }
    await load();
  };

  const reject = async () => {
    if (!rejectFor) return;
    setBusyId(rejectFor.id);
    setActionError(null);
    const res = await apiRequest(
      `/admin/dive-site-contributions/${rejectFor.id}/reject`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: rejectReason.trim() || undefined }),
      },
    );
    setBusyId(null);
    if (!res.ok) {
      setActionError(res.errorMessage || "Не удалось отклонить");
      return;
    }
    setRejectFor(null);
    setRejectReason("");
    await load();
  };

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">
          Заявки на дайв-сайты
        </h1>
        <p className="text-sm text-zinc-400">
          Новые точки и исправления существующих карточек из приложения. Одобряют
          только <strong>ADMIN</strong> и <strong>SUPER_ADMIN</strong>. Если список
          пустой — проверьте фильтр «Статус» (по умолчанию только «Ожидают») и что
          миграция <code className="text-zinc-500">030_dive_site_contributions</code>{" "}
          применена на сервере.
        </p>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm text-zinc-400">Статус</label>
        <select
          className="rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-zinc-100"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="pending">Ожидают</option>
          <option value="approved">Принятые</option>
          <option value="rejected">Отклонённые</option>
          <option value="all">Все</option>
        </select>
        <button
          type="button"
          className="rounded-lg border border-zinc-600 px-3 py-2 text-sm hover:bg-zinc-800"
          onClick={() => void load()}
        >
          Обновить
        </button>
      </div>

      {actionError ? (
        <p className="text-sm text-red-400">{actionError}</p>
      ) : null}
      {listError ? (
        <p className="text-sm text-red-400">{listError}</p>
      ) : null}

      {loading ? (
        <p className="text-sm text-zinc-500">Загрузка…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-zinc-500">Нет записей.</p>
      ) : (
        <ul className="space-y-4">
          {rows.map((row) => (
            <li
              key={row.id}
              className="rounded-xl border border-zinc-800 bg-zinc-950/60 p-4"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="space-y-1 text-sm">
                  <div className="flex flex-wrap gap-2">
                    <span className="rounded-md bg-zinc-800 px-2 py-0.5 text-xs">
                      {typeLabel(row.contribution_type)}
                    </span>
                    <span className="rounded-md bg-zinc-800 px-2 py-0.5 text-xs">
                      {statusLabel(row.status)}
                    </span>
                  </div>
                  <p className="text-xs text-zinc-500">
                    {new Date(row.created_at).toLocaleString("ru-RU")} ·{" "}
                    {row.submitterEmail ?? row.submitter_user_id}
                  </p>
                  {row.dive_site_id ? (
                    <p className="text-xs text-zinc-400">
                      Дайв-сайт:{" "}
                      <code className="text-zinc-300">{row.dive_site_id}</code>
                    </p>
                  ) : null}
                  {row.message ? (
                    <p className="pt-2 text-zinc-200">{row.message}</p>
                  ) : null}
                  <pre className="mt-2 max-h-48 overflow-auto rounded-lg bg-black/40 p-3 text-xs text-zinc-300">
                    {JSON.stringify(row.proposed_data, null, 2)}
                  </pre>
                </div>
                {row.status === "pending" ? (
                  <div className="flex shrink-0 flex-col gap-2">
                    <button
                      type="button"
                      disabled={busyId === row.id}
                      className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
                      onClick={() => void approve(row.id)}
                    >
                      {busyId === row.id ? "…" : "Одобрить"}
                    </button>
                    <button
                      type="button"
                      disabled={busyId === row.id}
                      className="rounded-lg border border-red-500/50 px-3 py-2 text-sm text-red-300 hover:bg-red-950/40 disabled:opacity-50"
                      onClick={() => {
                        setRejectFor(row);
                        setRejectReason("");
                        setActionError(null);
                      }}
                    >
                      Отклонить
                    </button>
                  </div>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}

      {rejectFor ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-md rounded-xl border border-zinc-700 bg-zinc-950 p-4 shadow-xl">
            <h2 className="text-lg font-medium">Отклонить заявку</h2>
            <p className="mt-2 text-sm text-zinc-400">
              Укажите причину (необязательно).
            </p>
            <textarea
              className="mt-3 w-full rounded-lg border border-zinc-700 bg-zinc-900 p-2 text-sm text-zinc-100"
              rows={4}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                className="rounded-lg px-3 py-2 text-sm text-zinc-400 hover:bg-zinc-800"
                onClick={() => setRejectFor(null)}
              >
                Отмена
              </button>
              <button
                type="button"
                disabled={busyId === rejectFor.id}
                className="rounded-lg bg-red-600 px-3 py-2 text-sm text-white hover:bg-red-500 disabled:opacity-50"
                onClick={() => void reject()}
              >
                Отклонить
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
