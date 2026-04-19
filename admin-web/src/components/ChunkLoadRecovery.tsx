"use client";

import { useEffect } from "react";

/**
 * После деплоя CDN/браузер иногда отдаёт старый HTML с путями к старым чанкам → белый экран.
 * Один автоматический reload при ChunkLoadError обычно подтягивает актуальную оболочку.
 */
export function ChunkLoadRecovery() {
  useEffect(() => {
    const key = "divehub_chunk_reload_once";
    const tryReload = () => {
      if (typeof window === "undefined") return;
      if (sessionStorage.getItem(key)) return;
      sessionStorage.setItem(key, "1");
      window.location.reload();
    };

    const onWindowError = (ev: ErrorEvent) => {
      const msg = `${ev.message ?? ""} ${(ev.error as Error | undefined)?.message ?? ""}`;
      if (
        /chunk load|loading chunk|ChunkLoadError|dynamically imported module|import\(\)/i.test(
          msg,
        )
      ) {
        tryReload();
      }
    };

    const onRejection = (ev: PromiseRejectionEvent) => {
      const r = ev.reason;
      const msg =
        typeof r === "string"
          ? r
          : r && typeof r === "object" && "message" in r
            ? String((r as { message?: unknown }).message)
            : "";
      if (
        /chunk load|ChunkLoadError|dynamically imported module/i.test(msg)
      ) {
        tryReload();
      }
    };

    window.addEventListener("error", onWindowError);
    window.addEventListener("unhandledrejection", onRejection);
    return () => {
      window.removeEventListener("error", onWindowError);
      window.removeEventListener("unhandledrejection", onRejection);
    };
  }, []);

  return null;
}
