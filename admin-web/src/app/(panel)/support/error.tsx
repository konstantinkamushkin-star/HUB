"use client";

import { useEffect } from "react";

/**
 * Ошибка на /support — с инлайн-стилями, чтобы не «белый лист» без CSS.
 */
export default function SupportRouteError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("admin-web /support error:", error);
  }, [error]);

  const bg = "#09090b";
  const fg = "#e4e4e7";
  const muted = "#a1a1aa";

  return (
    <div
      style={{
        minHeight: "50vh",
        backgroundColor: bg,
        color: fg,
        fontFamily: "system-ui, sans-serif",
        padding: 24,
      }}
    >
      <h1 style={{ fontSize: "1.125rem", fontWeight: 600, margin: "0 0 12px" }}>
        Не удалось открыть «Поддержку»
      </h1>
      <p style={{ fontSize: "0.875rem", color: muted, margin: "0 0 16px", maxWidth: 480 }}>
        Обновите страницу (Cmd+Shift+R). Если снова пусто — откройте консоль Safari
        (⌥⌘C) и проверьте, не падает ли загрузка скриптов с{" "}
        <code style={{ color: fg }}>/_next/static/</code>.
      </p>
      <button
        type="button"
        onClick={() => reset()}
        style={{
          border: "none",
          borderRadius: 8,
          padding: "10px 18px",
          fontSize: "0.875rem",
          fontWeight: 500,
          backgroundColor: "#0284c7",
          color: "#fff",
          cursor: "pointer",
        }}
      >
        Повторить
      </button>
    </div>
  );
}
