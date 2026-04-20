import Link from "next/link";
import { BrandLogo } from "@/components/BrandLogo";

export default function NotFound() {
  return (
    <div
      className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-16 text-zinc-100"
      style={{ backgroundColor: "#09090b" }}
    >
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -left-24 top-1/4 h-80 w-80 rounded-full bg-sky-600/25 blur-3xl" />
        <div className="absolute -right-20 bottom-1/4 h-96 w-96 rounded-full bg-cyan-500/20 blur-3xl" />
        <div className="absolute left-1/2 top-10 h-64 w-64 -translate-x-1/2 rounded-full bg-teal-500/15 blur-3xl" />
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage:
              "radial-gradient(circle at 2px 2px, rgb(148 163 184) 1px, transparent 0)",
            backgroundSize: "40px 40px",
          }}
        />
      </div>

      <div className="relative z-10 flex max-w-lg flex-col items-center text-center">
        <div className="mb-8 flex flex-col items-center gap-4">
          <BrandLogo variant="mark" className="h-16 w-16 shadow-lg shadow-sky-900/50" maskedMark />
          <BrandLogo variant="wordmark" className="h-8 w-auto max-w-[200px] opacity-90" />
        </div>

        <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.35em] text-sky-400/90">
          Страница не найдена
        </p>
        <h1 className="mt-3 text-7xl font-black tracking-tight text-white sm:text-8xl">
          404
        </h1>
        <p className="mt-6 text-lg leading-relaxed text-zinc-400">
          Похоже, эта страница уплыла глубже, чем мы ныряли. Проверьте адрес или
          вернитесь на берег — там всё спокойнее.
        </p>
        <p className="mt-2 text-4xl" aria-hidden>
          🤿
        </p>

        <div className="mt-10 flex flex-col gap-3 sm:flex-row sm:justify-center">
          <Link
            href="/"
            className="inline-flex min-w-[200px] items-center justify-center rounded-xl bg-gradient-to-r from-sky-500 to-cyan-500 px-6 py-3.5 text-sm font-semibold text-white shadow-lg shadow-sky-900/40 transition hover:from-sky-400 hover:to-cyan-400"
          >
            На главную
          </Link>
          <Link
            href="/privacy"
            className="inline-flex min-w-[200px] items-center justify-center rounded-xl border border-zinc-700 bg-zinc-900/80 px-6 py-3.5 text-sm font-medium text-zinc-200 transition hover:border-sky-600/60 hover:bg-zinc-800"
          >
            Конфиденциальность
          </Link>
        </div>
      </div>
    </div>
  );
}
