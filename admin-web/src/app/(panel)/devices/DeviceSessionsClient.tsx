"use client";

import { useCallback, useState } from "react";
import { useSearchParams } from "next/navigation";
import { apiGet } from "@/lib/api";

export function DeviceSessionsClient() {
  const params = useSearchParams();
  const initial = params.get("userId") ?? "";
  const [userId, setUserId] = useState(initial);
  const [body, setBody] = useState<string>("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    const id = userId.trim();
    if (!id) {
      setBody(JSON.stringify({ error: "Укажите UUID пользователя" }, null, 2));
      return;
    }
    setLoading(true);
    const res = await apiGet(`/admin/device-sessions/users/${id}/devices`);
    setLoading(false);
    if (!res.ok) {
      setBody(JSON.stringify({ error: res.errorMessage, status: res.status }, null, 2));
    } else {
      setBody(JSON.stringify(res.data, null, 2));
    }
  }, [userId]);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-white">Устройства и сессии</h1>
      <p className="max-w-2xl text-sm text-zinc-400">
        Список push-устройств как прокси сессий. Требуется{" "}
        <code className="rounded bg-zinc-800 px-1">manage:users</code>.
      </p>
      <div className="flex flex-wrap gap-2">
        <input
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          placeholder="UUID пользователя"
          className="min-w-[280px] flex-1 rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
        >
          {loading ? "…" : "Загрузить"}
        </button>
      </div>
      <pre className="max-h-[calc(100vh-16rem)] overflow-auto rounded-lg border border-zinc-800 bg-zinc-900/50 p-4 text-xs text-zinc-300">
        {body || "Нажмите «Загрузить»."}
      </pre>
    </div>
  );
}
