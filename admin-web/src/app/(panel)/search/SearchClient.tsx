"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";

export function SearchClient() {
  const params = useSearchParams();
  const query = params.get("query") ?? "";
  const [body, setBody] = useState<string>("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      setBody(
        JSON.stringify(
          { hint: "Укажите в URL параметр ?query= (минимум 2 символа)" },
          null,
          2,
        ),
      );
      return;
    }
    let c = false;
    setLoading(true);
    void (async () => {
      const res = await apiGet(
        `/admin/search?query=${encodeURIComponent(query)}&limit=20`,
      );
      if (c) return;
      setLoading(false);
      if (!res.ok) {
        setBody(JSON.stringify({ error: res.errorMessage, status: res.status }, null, 2));
      } else {
        setBody(JSON.stringify(res.data, null, 2));
      }
    })();
    return () => {
      c = true;
    };
  }, [query]);

  return (
    <div className="space-y-3">
      <h1 className="text-xl font-semibold text-white">Глобальный поиск</h1>
      <p className="text-sm text-zinc-500">
        Запрос: <span className="text-zinc-300">{query || "—"}</span>
        {loading ? " (загрузка…)" : ""}
      </p>
      <pre className="max-h-[calc(100vh-12rem)] overflow-auto rounded-lg border border-zinc-800 bg-zinc-900/50 p-4 text-xs text-zinc-300">
        {body}
      </pre>
    </div>
  );
}
