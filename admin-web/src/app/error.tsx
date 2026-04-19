"use client";

import { useEffect } from "react";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("admin-web route error:", error);
  }, [error]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-zinc-950 px-6 text-center text-zinc-100">
      <h1 className="text-lg font-semibold">Не удалось загрузить страницу</h1>
      <p className="max-w-md text-sm text-zinc-400">
        После обновления сайта иногда мешает кэш браузера. Попробуйте жёсткое
        обновление:{" "}
        <kbd className="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-xs">
          Ctrl+Shift+R
        </kbd>{" "}
        (Mac:{" "}
        <kbd className="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-xs">
          Cmd+Shift+R
        </kbd>
        ).
      </p>
      <button
        type="button"
        className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500"
        onClick={() => reset()}
      >
        Повторить
      </button>
    </div>
  );
}
