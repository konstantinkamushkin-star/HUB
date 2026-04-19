"use client";

import { useEffect } from "react";

/**
 * Ошибки в корневом layout — без этого Next может показать пустой экран.
 * Должны быть собственные html и body.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("admin-web global error:", error);
  }, [error]);
  const bg = "#09090b";
  const fg = "#e4e4e7";
  const muted = "#a1a1aa";

  return (
    <html lang="ru" style={{ backgroundColor: bg }}>
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          backgroundColor: bg,
          color: fg,
          fontFamily: "system-ui, sans-serif",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
          textAlign: "center",
        }}
      >
        <h1 style={{ fontSize: "1.125rem", fontWeight: 600, margin: "0 0 12px" }}>
          Критическая ошибка интерфейса
        </h1>
        <p style={{ maxWidth: 420, fontSize: "0.875rem", color: muted, margin: "0 0 20px" }}>
          Обновите страницу с очисткой кэша (Ctrl+Shift+R / Cmd+Shift+R). Если
          повторяется — проверьте, что на сервере задеплоена полная сборка{" "}
          <code style={{ color: fg }}>admin-web</code> (папка{" "}
          <code style={{ color: fg }}>.next</code>).
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
      </body>
    </html>
  );
}
