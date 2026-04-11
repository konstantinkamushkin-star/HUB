"use client";

import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";

export function JsonPanel({
  title,
  description,
  apiPath,
}: {
  title: string;
  description?: string;
  apiPath: string;
}) {
  const [loading, setLoading] = useState(true);
  const [body, setBody] = useState<string>("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      const res = await apiGet(apiPath);
      if (cancelled) return;
      if (!res.ok) {
        setBody(
          JSON.stringify(
            { error: res.errorMessage, status: res.status },
            null,
            2,
          ),
        );
      } else {
        setBody(JSON.stringify(res.data, null, 2));
      }
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [apiPath]);

  return (
    <div className="space-y-3">
      <div>
        <h1 className="text-xl font-semibold text-white">{title}</h1>
        {description ? (
          <p className="mt-1 max-w-3xl text-sm text-zinc-400">{description}</p>
        ) : null}
      </div>
      <pre className="max-h-[calc(100vh-12rem)] overflow-auto rounded-lg border border-zinc-800 bg-zinc-900/50 p-4 text-xs text-zinc-300">
        {loading ? "Загрузка…" : body}
      </pre>
    </div>
  );
}
