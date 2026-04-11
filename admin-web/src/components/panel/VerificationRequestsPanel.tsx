"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { apiGet, apiRequest } from "@/lib/api";

type VerificationRequest = {
  id: string;
  targetType: string;
  targetId: string;
  status: string;
  attemptNumber: number;
  documents: Record<string, unknown> | null;
  decisionNote: string | null;
  handledByAdminId: string | null;
  history: unknown[] | null;
  createdAt: string;
  updatedAt: string;
};

type ModalAction = "verify" | "reject" | "contact";

function docStr(
  docs: Record<string, unknown> | null | undefined,
  key: string,
): string {
  const v = docs?.[key];
  if (typeof v === "string") return v;
  if (v == null) return "";
  return String(v);
}

function statusLabel(status: string): string {
  switch (status) {
    case "pending":
      return "Ожидает";
    case "verified":
      return "Принято";
    case "rejected":
      return "Отклонено";
    case "more_info":
      return "Нужна информация";
    case "revoked":
      return "Отозвано";
    default:
      return status;
  }
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case "pending":
      return "bg-amber-500/15 text-amber-300 ring-amber-500/40";
    case "verified":
      return "bg-emerald-500/15 text-emerald-300 ring-emerald-500/40";
    case "rejected":
      return "bg-red-500/15 text-red-300 ring-red-500/40";
    case "more_info":
      return "bg-sky-500/15 text-sky-300 ring-sky-500/40";
    case "revoked":
      return "bg-zinc-500/15 text-zinc-400 ring-zinc-500/40";
    default:
      return "bg-zinc-500/15 text-zinc-300 ring-zinc-500/40";
  }
}

function targetLabel(t: string): string {
  if (t === "dive_center") return "Дайв-центр";
  if (t === "shop") return "Магазин";
  if (t === "user") return "Пользователь";
  return t;
}

export function VerificationRequestsPanel() {
  const [rows, setRows] = useState<VerificationRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [modal, setModal] = useState<{
    action: ModalAction;
    row: VerificationRequest;
  } | null>(null);
  const [reason, setReason] = useState("");
  const [decisionNote, setDecisionNote] = useState("");
  const [modalError, setModalError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setListError(null);
    const res = await apiGet<VerificationRequest[]>(
      "/admin/verification/requests?limit=200",
    );
    if (!res.ok) {
      setListError(res.errorMessage || "Не удалось загрузить заявки");
      setRows([]);
    } else {
      setRows(Array.isArray(res.data) ? res.data : []);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openModal = (action: ModalAction, row: VerificationRequest) => {
    setModal({ action, row });
    setReason("");
    setDecisionNote(row.decisionNote ?? "");
    setModalError(null);
  };

  const closeModal = () => {
    setModal(null);
    setReason("");
    setDecisionNote("");
    setModalError(null);
  };

  const submitModal = async () => {
    if (!modal) return;
    const r = reason.trim();
    if (r.length < 3) {
      setModalError("Укажите комментарий для журнала аудита (не менее 3 символов).");
      return;
    }

    const status =
      modal.action === "verify"
        ? "verified"
        : modal.action === "reject"
          ? "rejected"
          : "more_info";

    setBusyId(modal.row.id);
    setModalError(null);
    const res = await apiRequest<VerificationRequest>(
      `/admin/verification/requests/${modal.row.id}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          "X-Admin-Confirm-Dangerous-Action": "true",
        },
        body: JSON.stringify({
          status,
          reason: r,
          decisionNote: decisionNote.trim() || undefined,
        }),
      },
    );
    setBusyId(null);

    if (!res.ok) {
      setModalError(res.errorMessage || "Ошибка сохранения");
      return;
    }
    closeModal();
    await load();
  };

  const pendingCount = useMemo(
    () => rows.filter((x) => x.status === "pending" || x.status === "more_info").length,
    [rows],
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-white">Верификация</h1>
          <p className="mt-1 max-w-3xl text-sm text-zinc-400">
            Заявки с публичной формы регистрации бизнеса. Решение — только у роли с правом{" "}
            <code className="rounded bg-zinc-800 px-1 text-zinc-300">verify:entities</code>.
            Для каждого действия нужен комментарий в журнал аудита (мин. 3 символа).
          </p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="rounded-md border border-zinc-600 px-3 py-2 text-sm text-zinc-200 hover:bg-zinc-800 disabled:opacity-50"
        >
          Обновить
        </button>
      </div>

      {listError ? (
        <div className="rounded-lg border border-red-900/50 bg-red-950/40 px-4 py-3 text-sm text-red-200">
          {listError}
        </div>
      ) : null}

      {loading ? (
        <p className="text-sm text-zinc-500">Загрузка…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-zinc-500">Заявок пока нет.</p>
      ) : (
        <p className="text-sm text-zinc-500">
          Всего: {rows.length}
          {pendingCount > 0 ? ` · Требуют решения: ${pendingCount}` : null}
        </p>
      )}

      <ul className="space-y-4">
        {rows.map((row) => {
          const docs = row.documents;
          const name = docStr(docs, "name");
          const email = docStr(docs, "contactEmail");
          const phone = docStr(docs, "contactPhone");
          const city = docStr(docs, "city");
          const country = docStr(docs, "country");
          const kind = docStr(docs, "kind");
          const canAct = row.status === "pending" || row.status === "more_info";
          const mailto =
            email.length > 0
              ? `mailto:${encodeURIComponent(email)}?subject=${encodeURIComponent(
                  "DiveHub — заявка на верификацию",
                )}&body=${encodeURIComponent(
                  `Здравствуйте!\n\nПо вашей заявке (ID ${row.id.slice(0, 8)}…):\n\n`,
                )}`
              : "";

          return (
            <li
              key={row.id}
              className="rounded-xl border border-zinc-800 bg-zinc-900/40 p-4 shadow-sm"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${statusBadgeClass(row.status)}`}
                    >
                      {statusLabel(row.status)}
                    </span>
                    <span className="text-sm text-zinc-500">
                      {targetLabel(row.targetType)} ·{" "}
                      <code className="text-zinc-400">{row.targetId}</code>
                    </span>
                  </div>
                  <div className="text-base font-medium text-white">
                    {name || "Без названия"}
                    {kind ? (
                      <span className="ml-2 text-sm font-normal text-zinc-500">
                        ({kind === "dive_center" ? "дайв-центр" : kind === "shop" ? "магазин" : kind})
                      </span>
                    ) : null}
                  </div>
                  <div className="text-sm text-zinc-400">
                    {[city, country].filter(Boolean).join(", ") || "Адрес не указан"}
                  </div>
                  {email ? (
                    <div className="text-sm">
                      <span className="text-zinc-500">Email: </span>
                      <a
                        href={`mailto:${email}`}
                        className="text-sky-400 hover:text-sky-300 hover:underline"
                      >
                        {email}
                      </a>
                    </div>
                  ) : (
                    <div className="text-sm text-amber-500/90">
                      В заявке нет contactEmail — письмо партнёру вручную по другим данным.
                    </div>
                  )}
                  {phone ? (
                    <div className="text-sm text-zinc-400">
                      Тел.:{" "}
                      <a href={`tel:${phone.replace(/\s/g, "")}`} className="hover:text-zinc-200">
                        {phone}
                      </a>
                    </div>
                  ) : null}
                  <div className="text-xs text-zinc-600">
                    Заявка · {new Date(row.createdAt).toLocaleString("ru-RU")} · попытка{" "}
                    {row.attemptNumber}
                  </div>
                  {row.decisionNote ? (
                    <div className="mt-2 rounded-md border border-zinc-700/80 bg-zinc-950/50 px-3 py-2 text-sm text-zinc-300">
                      <span className="text-zinc-500">Комментарий: </span>
                      {row.decisionNote}
                    </div>
                  ) : null}
                </div>

                <div className="flex w-full shrink-0 flex-col gap-2 sm:w-auto sm:min-w-[200px]">
                  {canAct ? (
                    <>
                      <button
                        type="button"
                        disabled={busyId === row.id}
                        onClick={() => openModal("verify", row)}
                        className="rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
                      >
                        Принять
                      </button>
                      <button
                        type="button"
                        disabled={busyId === row.id}
                        onClick={() => openModal("reject", row)}
                        className="rounded-lg bg-red-600/90 px-4 py-2.5 text-sm font-medium text-white hover:bg-red-500 disabled:opacity-50"
                      >
                        Отклонить
                      </button>
                      <button
                        type="button"
                        disabled={busyId === row.id}
                        onClick={() => openModal("contact", row)}
                        className="rounded-lg border border-sky-600/60 bg-sky-600/10 px-4 py-2.5 text-sm font-medium text-sky-200 hover:bg-sky-600/20 disabled:opacity-50"
                      >
                        Связаться с заявителем
                      </button>
                      {mailto ? (
                        <a
                          href={mailto}
                          className="rounded-lg border border-zinc-600 px-4 py-2.5 text-center text-sm text-zinc-300 hover:bg-zinc-800"
                        >
                          Открыть письмо…
                        </a>
                      ) : null}
                    </>
                  ) : (
                    <span className="text-xs text-zinc-600">Решение принято</span>
                  )}
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      {modal ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="verification-modal-title"
        >
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-zinc-700 bg-zinc-900 p-5 shadow-2xl">
            <h2 id="verification-modal-title" className="text-lg font-semibold text-white">
              {modal.action === "verify"
                ? "Принять заявку"
                : modal.action === "reject"
                  ? "Отклонить заявку"
                  : "Связаться с заявителем"}
            </h2>
            <p className="mt-2 text-sm text-zinc-400">
              {modal.action === "contact"
                ? "Статус заявки станет «Нужна информация». Опишите, что уточнить у заявителя; при необходимости используйте «Открыть письмо…» на карточке."
                : modal.action === "verify"
                  ? "Центр/магазин будет активирован, партнёру создадут вход (если в заявке указан email)."
                  : "Заявка будет отклонена, объект останется неактивным."}
            </p>

            <label className="mt-4 block text-sm font-medium text-zinc-300">
              Комментарий для журнала аудита <span className="text-red-400">*</span>
              <textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                rows={3}
                className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-sky-600"
                placeholder="Кратко: основание решения (обязательно, от 3 символов)"
              />
            </label>

            <label className="mt-3 block text-sm font-medium text-zinc-300">
              Заметка к заявке (необязательно)
              <textarea
                value={decisionNote}
                onChange={(e) => setDecisionNote(e.target.value)}
                rows={2}
                className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-sky-600"
                placeholder="Видна в истории заявки"
              />
            </label>

            {modalError ? (
              <p className="mt-3 text-sm text-red-400">{modalError}</p>
            ) : null}

            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                onClick={closeModal}
                disabled={busyId !== null}
                className="rounded-lg border border-zinc-600 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800 disabled:opacity-50"
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={() => void submitModal()}
                disabled={busyId !== null}
                className={`rounded-lg px-4 py-2 text-sm font-medium text-white disabled:opacity-50 ${
                  modal.action === "verify"
                    ? "bg-emerald-600 hover:bg-emerald-500"
                    : modal.action === "reject"
                      ? "bg-red-600 hover:bg-red-500"
                      : "bg-sky-600 hover:bg-sky-500"
                }`}
              >
                {busyId ? "Сохранение…" : "Подтвердить"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
