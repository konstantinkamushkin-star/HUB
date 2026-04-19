"use client";

import { Component, type ErrorInfo, type ReactNode, useCallback, useEffect, useState } from "react";
import { apiGet } from "@/lib/api";

type TicketRow = {
  id: string;
  subject: string;
  body: string;
  status: string;
  priority: string;
  category?: string;
  conversationId?: string | null;
  reporterEmail?: string | null;
  createdAt?: string;
};

function safeTicketRow(raw: unknown): TicketRow | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const id = o.id != null ? String(o.id) : "";
  if (!id) return null;
  return {
    id,
    subject: o.subject != null ? String(o.subject) : "—",
    body: o.body != null ? String(o.body) : "",
    status: o.status != null ? String(o.status) : "—",
    priority: o.priority != null ? String(o.priority) : "—",
    category: o.category != null ? String(o.category) : undefined,
    conversationId:
      o.conversationId == null ? null : String(o.conversationId),
    reporterEmail:
      o.reporterEmail == null ? null : String(o.reporterEmail),
    createdAt: o.createdAt != null ? String(o.createdAt) : undefined,
  };
}

function formatCreatedAt(iso: string | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString("ru-RU");
}

class SupportUiBoundary extends Component<
  { children: ReactNode },
  { err: Error | null }
> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { err: null };
  }

  static getDerivedStateFromError(err: Error) {
    return { err };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("SupportTicketsClient render error:", error, info.componentStack);
  }

  render() {
    if (this.state.err) {
      return (
        <div
          style={{
            backgroundColor: "#09090b",
            color: "#fca5a5",
            padding: 24,
            fontFamily: "system-ui, sans-serif",
            borderRadius: 8,
          }}
        >
          <p style={{ margin: "0 0 8px", fontWeight: 600 }}>Ошибка отображения списка</p>
          <p style={{ margin: 0, fontSize: 14, color: "#a1a1aa" }}>
            Обновите страницу. Если повторяется — сообщите разработчикам.
          </p>
        </div>
      );
    }
    return this.props.children;
  }
}

export function SupportTicketsClient() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<TicketRow[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiGet<{ items?: unknown[] }>(
        "/admin/support/tickets?limit=100",
      );
      if (!res.ok) {
        setError(res.errorMessage ?? "Ошибка загрузки");
        setRows([]);
        return;
      }
      const rawItems =
        res.data && typeof res.data === "object" && Array.isArray(res.data.items)
          ? res.data.items
          : [];
      const items: TicketRow[] = [];
      for (const r of rawItems) {
        const row = safeTicketRow(r);
        if (row) items.push(row);
      }
      setRows(items);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Неизвестная ошибка");
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <SupportUiBoundary>
    <div
      className="space-y-4"
      style={{
        minHeight: 120,
        backgroundColor: "#09090b",
        color: "#e4e4e7",
      }}
    >
      <div>
        <h1 className="text-xl font-semibold text-white">
          Поддержка (тикеты)
        </h1>
        <p className="mt-1 max-w-3xl text-sm text-zinc-400">
          Заявки из приложения и админки. Поля{" "}
          <code className="text-zinc-500">category</code> (feedback / bug /
          other) и <code className="text-zinc-500">conversationId</code> — связь
          с чатом.
        </p>
      </div>

      {error ? (
        <p className="text-sm text-red-400">{error}</p>
      ) : null}

      <div className="overflow-x-auto rounded-lg border border-zinc-800">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-zinc-800 bg-zinc-900/80 text-xs uppercase text-zinc-500">
            <tr>
              <th className="px-3 py-2 font-medium">Тема</th>
              <th className="px-3 py-2 font-medium">Категория</th>
              <th className="px-3 py-2 font-medium">Статус</th>
              <th className="px-3 py-2 font-medium">Чат</th>
              <th className="px-3 py-2 font-medium">Email</th>
              <th className="px-3 py-2 font-medium">Создан</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {loading ? (
              <tr>
                <td colSpan={6} className="px-3 py-6 text-zinc-500">
                  Загрузка…
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-3 py-6 text-zinc-500">
                  Нет тикетов
                </td>
              </tr>
            ) : (
              rows.map((t) => (
                <tr key={t.id} className="hover:bg-zinc-900/40">
                  <td className="max-w-xs px-3 py-2 text-zinc-200">
                    <span className="line-clamp-2 font-medium">{t.subject}</span>
                    <p className="mt-1 line-clamp-2 font-mono text-[11px] text-zinc-500">
                      {t.id}
                    </p>
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-zinc-300">
                    {t.category ?? "—"}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-zinc-300">
                    {t.status}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs text-zinc-400">
                    {t.conversationId ? (
                      <span className="break-all">{t.conversationId}</span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="max-w-[140px] truncate px-3 py-2 text-zinc-400">
                    {t.reporterEmail ?? "—"}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-zinc-500">
                    {formatCreatedAt(t.createdAt)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
    </SupportUiBoundary>
  );
}
