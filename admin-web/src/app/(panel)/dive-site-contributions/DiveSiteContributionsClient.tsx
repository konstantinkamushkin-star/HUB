"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { apiGet, apiRequest, type ApiResult } from "@/lib/api";
import { getStoredUser } from "@/lib/auth";

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

type SupportChatPayload = {
  conversationId: string | null;
  deepLink: string | null;
  submitterEmail: string | null;
};

type ThreadMsg = {
  id: string;
  senderId: string;
  senderName?: string;
  content: string;
  messageType?: string;
  createdAt: string;
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

function pickString(data: Record<string, unknown>, key: string): string | null {
  const v = data[key];
  if (typeof v === "string" && v.trim()) return v.trim();
  return null;
}

function pickNum(data: Record<string, unknown>, ...keys: string[]): number | null {
  for (const k of keys) {
    const v = data[k];
    if (typeof v === "number" && Number.isFinite(v)) return v;
    if (typeof v === "string" && v.trim()) {
      const n = Number(v);
      if (Number.isFinite(n)) return n;
    }
  }
  return null;
}

function formatList(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v.map((x) => String(x)).filter(Boolean);
}

function ProposedDataView({ data }: { data: Record<string, unknown> }) {
  const name =
    pickString(data, "name") ?? pickString(data, "localized_name") ?? "—";
  const country = pickString(data, "country");
  const region = pickString(data, "region");
  const address = pickString(data, "address");
  const description = pickString(data, "description");
  const lat = pickNum(data, "latitude", "lat");
  const lng = pickNum(data, "longitude", "lng", "lon");
  const depthMin = pickNum(data, "depth_min");
  const depthMax = pickNum(data, "depth_max");
  const diff = data.difficulty_level;
  const siteTypes = formatList(data.site_types);
  const accessTypes = formatList(data.access_type);
  const marine = formatList(data.marine_life);
  const photos = formatList(data.photo_urls);
  const videos = formatList(data.video_urls);

  const mapHref =
    lat != null && lng != null
      ? `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=14/${lat}/${lng}`
      : null;

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-lg font-semibold leading-tight text-white">{name}</h3>
        {(country || region) && (
          <p className="mt-1 text-sm text-zinc-400">
            {[country, region].filter(Boolean).join(" · ")}
          </p>
        )}
      </div>

      <dl className="grid gap-4 sm:grid-cols-2">
        {address ? (
          <div className="sm:col-span-2">
            <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Адрес
            </dt>
            <dd className="mt-0.5 text-sm text-zinc-200">{address}</dd>
          </div>
        ) : null}

        {description ? (
          <div className="sm:col-span-2">
            <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Описание
            </dt>
            <dd className="mt-0.5 whitespace-pre-wrap text-sm leading-relaxed text-zinc-300">
              {description}
            </dd>
          </div>
        ) : null}

        {lat != null && lng != null ? (
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Координаты
            </dt>
            <dd className="mt-0.5 font-mono text-sm text-emerald-300/95">
              {lat.toFixed(6)}, {lng.toFixed(6)}
              {mapHref ? (
                <a
                  href={mapHref}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="ml-2 inline-block text-xs font-sans text-sky-400 underline decoration-sky-400/40 underline-offset-2 hover:text-sky-300"
                >
                  Карта ↗
                </a>
              ) : null}
            </dd>
          </div>
        ) : null}

        {(depthMin != null || depthMax != null) && (
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Глубина, м
            </dt>
            <dd className="mt-0.5 text-sm text-zinc-200">
              {depthMin != null ? depthMin : "—"} — {depthMax != null ? depthMax : "—"}
            </dd>
          </div>
        )}

        {diff !== undefined && diff !== null ? (
          <div>
            <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Сложность
            </dt>
            <dd className="mt-0.5 text-sm text-zinc-200">{String(diff)}</dd>
          </div>
        ) : null}
      </dl>

      {siteTypes.length > 0 ? (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Типы сайта
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {siteTypes.map((t) => (
              <span
                key={t}
                className="rounded-full bg-teal-950/80 px-2.5 py-0.5 text-xs text-teal-200 ring-1 ring-teal-700/50"
              >
                {t}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      {accessTypes.length > 0 ? (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Доступ
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {accessTypes.map((t) => (
              <span
                key={t}
                className="rounded-full bg-indigo-950/70 px-2.5 py-0.5 text-xs text-indigo-200 ring-1 ring-indigo-700/40"
              >
                {t}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      {marine.length > 0 ? (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">
            Обитатели / интерес
          </p>
          <p className="mt-1 text-sm text-zinc-400">{marine.join(" · ")}</p>
        </div>
      ) : null}

      {(photos.length > 0 || videos.length > 0) && (
        <p className="text-xs text-zinc-500">
          Вложения:{" "}
          {photos.length > 0 ? `${photos.length} фото` : null}
          {photos.length > 0 && videos.length > 0 ? ", " : null}
          {videos.length > 0 ? `${videos.length} видео` : null}
        </p>
      )}

      <details className="group rounded-lg border border-zinc-800 bg-black/25">
        <summary className="cursor-pointer list-none px-3 py-2 text-xs text-zinc-500 transition hover:text-zinc-400">
          <span className="group-open:hidden">Все поля (JSON) ▾</span>
          <span className="hidden group-open:inline">Все поля (JSON) ▴</span>
        </summary>
        <pre className="max-h-56 overflow-auto border-t border-zinc-800/80 p-3 font-mono text-[11px] leading-relaxed text-zinc-400">
          {JSON.stringify(data, null, 2)}
        </pre>
      </details>
    </div>
  );
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

  const [chatRow, setChatRow] = useState<ContributionRow | null>(null);
  const [chatLoading, setChatLoading] = useState(false);
  const [chatInfo, setChatInfo] = useState<SupportChatPayload | null>(null);
  const [chatError, setChatError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [threadMessages, setThreadMessages] = useState<ThreadMsg[]>([]);
  const [threadMsgLoading, setThreadMsgLoading] = useState(false);
  const [threadMsgError, setThreadMsgError] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [sendBusy, setSendBusy] = useState(false);
  const threadEndRef = useRef<HTMLDivElement>(null);

  const loadThreadMessages = useCallback(async (conversationId: string) => {
    setThreadMsgLoading(true);
    setThreadMsgError(null);
    const res = await apiGet<{ messages?: ThreadMsg[] }>(
      `/chat/${conversationId}/messages?limit=80`,
    );
    setThreadMsgLoading(false);
    if (!res.ok) {
      setThreadMsgError(res.errorMessage ?? "Не удалось загрузить сообщения");
      setThreadMessages([]);
      return;
    }
    const raw = res.data;
    const list =
      raw && typeof raw === "object" && Array.isArray(raw.messages)
        ? raw.messages
        : [];
    setThreadMessages(list);
  }, []);

  useEffect(() => {
    if (!threadMessages.length) return;
    threadEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [threadMessages]);

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

  const openSupportChat = async (row: ContributionRow) => {
    setChatRow(row);
    setChatInfo(null);
    setChatError(null);
    setCopied(false);
    setThreadMessages([]);
    setThreadMsgError(null);
    setDraft("");
    setChatLoading(true);

    type ChatEnvelope = { success?: boolean; data?: SupportChatPayload };
    const candidates = [
      `/admin/dive-site-contributions/support-chat?contributionId=${encodeURIComponent(row.id)}`,
      `/admin/dive-site-contributions/support-chat/${row.id}`,
      `/admin/dive-site-contributions/${row.id}/support-chat`,
    ];

    let res: ApiResult<ChatEnvelope> | null = null;
    let lastMessage: string | null = null;
    for (const path of candidates) {
      const r = await apiGet<ChatEnvelope>(path);
      if (r.ok) {
        res = r;
        break;
      }
      lastMessage = r.errorMessage || `HTTP ${r.status}`;
      if (r.status !== 404) {
        res = r;
        break;
      }
    }

    setChatLoading(false);
    if (!res?.ok) {
      setChatError(lastMessage || "Не удалось получить чат");
      return;
    }
    const body = res.data;
    const payload =
      body && typeof body === "object" && body.data && typeof body.data === "object"
        ? body.data
        : null;
    if (!payload) {
      setChatError("Пустой ответ сервера");
      return;
    }
    setChatInfo(payload);
    if (payload.conversationId) {
      await loadThreadMessages(payload.conversationId);
    }
  };

  const sendThreadMessage = async () => {
    const cid = chatInfo?.conversationId;
    const text = draft.trim();
    if (!cid || !text) return;
    setSendBusy(true);
    setThreadMsgError(null);
    const res = await apiRequest<ThreadMsg>("/chat/messages", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        conversationId: cid,
        content: text,
        messageType: "text",
      }),
    });
    setSendBusy(false);
    if (!res.ok) {
      setThreadMsgError(res.errorMessage ?? "Не удалось отправить");
      return;
    }
    setDraft("");
    if (res.data && typeof res.data === "object" && "id" in res.data) {
      setThreadMessages((prev) => [...prev, res.data as ThreadMsg]);
    } else {
      await loadThreadMessages(cid);
    }
  };

  const copyDeepLink = async (link: string) => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setChatError("Не удалось скопировать — выделите ссылку вручную");
    }
  };

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
    <div className="space-y-8">
      <header className="space-y-2 border-b border-zinc-800/80 pb-6">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold tracking-tight text-white">
            Заявки на дайв-сайты
          </h1>
          <span
            className="rounded-md border border-emerald-700/50 bg-emerald-950/50 px-2 py-0.5 font-mono text-[11px] text-emerald-200/90"
            title="Если этой метки нет — открыта старая сборка: пересоберите admin-web на сервере и обновите страницу (в приложении — кнопка ⟳)."
          >
            UI {process.env.NEXT_PUBLIC_GIT_SHA ?? "—"}
          </span>
        </div>
        <p className="max-w-3xl text-sm leading-relaxed text-zinc-400">
          Новые точки и правки карточек из приложения. Модераторы:{" "}
          <strong className="text-zinc-300">ADMIN</strong> и{" "}
          <strong className="text-zinc-300">SUPER_ADMIN</strong>. Чат с автором
          ведётся в приложении DiveHub — нажмите «Чат с автором», скопируйте
          ссылку и откройте на телефоне с установленным приложением.
        </p>
        <p className="text-xs text-amber-200/80">
          Не видите кнопку «Чат с автором» и зелёную метку сборки? Выполните{" "}
          <code className="rounded bg-zinc-800 px-1">npm run build</code> в{" "}
          <code className="rounded bg-zinc-800 px-1">admin-web</code>, задеплойте
          артефакты и в iOS нажмите ⟳ в админ-панели.
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
        <ul className="space-y-6">
          {rows.map((row) => (
            <li key={row.id}>
              <article className="overflow-hidden rounded-2xl border border-zinc-700/60 bg-gradient-to-br from-zinc-900/95 via-zinc-950 to-black/90 shadow-xl shadow-black/30 ring-1 ring-white/5">
                <div className="flex flex-col gap-5 p-5 md:flex-row md:items-stretch md:justify-between md:gap-6">
                  <div className="min-w-0 flex-1 space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-lg bg-teal-950/90 px-2.5 py-1 text-xs font-medium text-teal-200 ring-1 ring-teal-700/40">
                        {typeLabel(row.contribution_type)}
                      </span>
                      <span
                        className={`rounded-lg px-2.5 py-1 text-xs font-medium ring-1 ${
                          row.status === "pending"
                            ? "bg-amber-950/80 text-amber-200 ring-amber-700/40"
                            : row.status === "approved"
                              ? "bg-emerald-950/80 text-emerald-200 ring-emerald-700/40"
                              : "bg-red-950/70 text-red-200 ring-red-800/40"
                        }`}
                      >
                        {statusLabel(row.status)}
                      </span>
                    </div>
                    <p className="text-xs text-zinc-500">
                      {new Date(row.created_at).toLocaleString("ru-RU")} ·{" "}
                      <span className="text-zinc-400">
                        {row.submitterEmail ?? row.submitter_user_id}
                      </span>
                    </p>
                    {row.dive_site_id ? (
                      <p className="font-mono text-xs text-zinc-500">
                        ID сайта: {row.dive_site_id}
                      </p>
                    ) : null}
                    {row.message ? (
                      <div className="rounded-xl border border-zinc-700/50 bg-zinc-900/40 px-4 py-3">
                        <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">
                          Комментарий автора
                        </p>
                        <p className="mt-1 text-sm text-zinc-200">{row.message}</p>
                      </div>
                    ) : null}
                    {row.status !== "pending" && row.rejection_reason ? (
                      <p className="text-sm text-red-300/90">
                        Причина отклонения: {row.rejection_reason}
                      </p>
                    ) : null}
                    <div className="rounded-xl border border-zinc-800/80 bg-black/20 p-4">
                      <ProposedDataView data={row.proposed_data} />
                    </div>
                  </div>

                  <div className="flex shrink-0 flex-col justify-start gap-2 border-t border-zinc-800/60 pt-4 md:w-44 md:border-l md:border-t-0 md:pl-5 md:pt-0">
                    <button
                      type="button"
                      disabled={busyId === row.id}
                      className="rounded-xl border border-sky-600/50 bg-sky-950/40 px-3 py-2.5 text-sm font-medium text-sky-100 transition hover:bg-sky-900/50 disabled:opacity-50"
                      onClick={() => void openSupportChat(row)}
                    >
                      💬 Чат с автором
                    </button>
                    {row.status === "pending" ? (
                      <>
                        <button
                          type="button"
                          disabled={busyId === row.id}
                          className="rounded-xl bg-emerald-600 px-3 py-2.5 text-sm font-medium text-white shadow-lg shadow-emerald-900/20 hover:bg-emerald-500 disabled:opacity-50"
                          onClick={() => void approve(row.id)}
                        >
                          {busyId === row.id ? "…" : "Одобрить"}
                        </button>
                        <button
                          type="button"
                          disabled={busyId === row.id}
                          className="rounded-xl border border-red-500/50 px-3 py-2.5 text-sm text-red-200 hover:bg-red-950/40 disabled:opacity-50"
                          onClick={() => {
                            setRejectFor(row);
                            setRejectReason("");
                            setActionError(null);
                          }}
                        >
                          Отклонить
                        </button>
                      </>
                    ) : null}
                  </div>
                </div>
              </article>
            </li>
          ))}
        </ul>
      )}

      {rejectFor ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="w-full max-w-md rounded-xl border border-zinc-700 bg-zinc-950 p-4 shadow-xl">
            <h2 className="text-lg font-medium text-white">Отклонить заявку</h2>
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

      {chatRow ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl border border-zinc-600/50 bg-zinc-950 shadow-2xl">
            <div className="shrink-0 border-b border-zinc-800 p-5 pb-3">
              <h2 className="text-lg font-semibold text-white">Чат по заявке</h2>
              <p className="mt-1 text-sm text-zinc-400">
                Тот же тред, что в приложении: автор заявки и поддержка. Ниже можно
                писать из браузера.
              </p>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
              {chatLoading ? (
                <p className="text-sm text-zinc-500">Подготовка чата…</p>
              ) : null}
              {chatError ? (
                <p className="text-sm text-red-400">{chatError}</p>
              ) : null}
              {chatInfo && !chatLoading ? (
                <div className="space-y-4">
                  {chatInfo.submitterEmail ? (
                    <p className="text-sm">
                      <span className="text-zinc-500">Email автора: </span>
                      <a
                        href={`mailto:${encodeURIComponent(chatInfo.submitterEmail)}`}
                        className="text-sky-400 underline decoration-sky-500/30 underline-offset-2 hover:text-sky-300"
                      >
                        {chatInfo.submitterEmail}
                      </a>
                    </p>
                  ) : null}
                  {chatInfo.conversationId && chatInfo.deepLink ? (
                    <details className="rounded-lg border border-zinc-800 bg-zinc-900/40 text-sm">
                      <summary className="cursor-pointer px-3 py-2 text-zinc-400">
                        Ссылка в приложение
                      </summary>
                      <div className="border-t border-zinc-800 px-3 py-2">
                        <div className="flex gap-2">
                          <input
                            readOnly
                            className="min-w-0 flex-1 rounded border border-zinc-700 bg-zinc-950 px-2 py-1.5 font-mono text-xs text-zinc-300"
                            value={chatInfo.deepLink}
                          />
                          <button
                            type="button"
                            className="shrink-0 rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-200 hover:bg-zinc-700"
                            onClick={() => void copyDeepLink(chatInfo.deepLink!)}
                          >
                            {copied ? "✓" : "Копировать"}
                          </button>
                        </div>
                        <p className="mt-2 text-xs text-zinc-500">
                          ID беседы:{" "}
                          <code className="text-zinc-400">{chatInfo.conversationId}</code>
                        </p>
                      </div>
                    </details>
                  ) : null}
                  {!chatInfo.conversationId ? (
                    <p className="rounded-lg border border-amber-800/50 bg-amber-950/30 px-3 py-2 text-sm text-amber-200/90">
                      Чат ещё не создан. На сервере задайте пользователя поддержки:
                      переменная окружения{" "}
                      <code className="rounded bg-black/40 px-1 text-xs">
                        DIVE_SITE_SUPPORT_ADMIN_USER_ID
                      </code>{" "}
                      (UUID) или убедитесь, что в базе есть ADMIN / SUPER_ADMIN.
                    </p>
                  ) : (
                    <>
                      {threadMsgLoading ? (
                        <p className="text-sm text-zinc-500">Загрузка сообщений…</p>
                      ) : null}
                      {threadMsgError ? (
                        <p className="text-sm text-red-400">{threadMsgError}</p>
                      ) : null}
                      <div className="max-h-72 space-y-2 overflow-y-auto rounded-lg border border-zinc-800 bg-zinc-900/50 p-3">
                        {threadMessages.length === 0 && !threadMsgLoading ? (
                          <p className="text-center text-sm text-zinc-500">
                            Пока нет сообщений — напишите первым.
                          </p>
                        ) : null}
                        {threadMessages.map((m) => {
                          const me = getStoredUser()?.id === m.senderId;
                          return (
                            <div
                              key={m.id}
                              className={`flex flex-col gap-0.5 rounded-lg px-3 py-2 text-sm ${
                                me
                                  ? "ml-8 bg-sky-950/50 text-zinc-100"
                                  : "mr-8 bg-zinc-800/80 text-zinc-200"
                              }`}
                            >
                              <div className="flex items-baseline justify-between gap-2">
                                <span className="font-medium text-zinc-300">
                                  {m.senderName ?? "Участник"}
                                </span>
                                <span className="shrink-0 text-[10px] text-zinc-500">
                                  {new Date(m.createdAt).toLocaleString("ru-RU", {
                                    day: "2-digit",
                                    month: "2-digit",
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })}
                                </span>
                              </div>
                              <p className="whitespace-pre-wrap break-words leading-relaxed">
                                {m.content}
                              </p>
                            </div>
                          );
                        })}
                        <div ref={threadEndRef} />
                      </div>
                      <div className="flex gap-2">
                        <textarea
                          className="min-h-[44px] flex-1 resize-none rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-zinc-100 placeholder:text-zinc-600"
                          placeholder="Сообщение автору…"
                          rows={2}
                          value={draft}
                          onChange={(e) => setDraft(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey) {
                              e.preventDefault();
                              void sendThreadMessage();
                            }
                          }}
                        />
                        <button
                          type="button"
                          disabled={sendBusy || !draft.trim()}
                          className="shrink-0 self-end rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
                          onClick={() => void sendThreadMessage()}
                        >
                          {sendBusy ? "…" : "Отправить"}
                        </button>
                      </div>
                    </>
                  )}
                </div>
              ) : null}
            </div>
            <div className="shrink-0 border-t border-zinc-800 p-4">
              <div className="flex justify-end">
                <button
                  type="button"
                  className="rounded-lg px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
                  onClick={() => {
                    setChatRow(null);
                    setChatInfo(null);
                    setChatError(null);
                    setThreadMessages([]);
                    setThreadMsgError(null);
                    setDraft("");
                  }}
                >
                  Закрыть
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
