"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BrandLogo } from "@/components/BrandLogo";

const navClass =
  "text-sm font-medium text-slate-600 transition-colors hover:text-sky-700";

export function PublicHeader() {
  const pathname = usePathname();
  const onRegister = pathname === "/register";

  return (
    <header className="relative z-20 border-b border-sky-100/70 bg-white/80 shadow-sm shadow-sky-900/[0.03] backdrop-blur-xl">
      <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-3.5 sm:px-6">
        <Link href="/" className="flex shrink-0 items-center gap-2.5">
          <BrandLogo className="h-9 w-9 shrink-0 rounded-lg shadow-sm shadow-sky-900/10" />
          <span className="text-sm font-bold uppercase tracking-[0.18em] text-slate-800">
            DiveHub
          </span>
        </Link>

        <nav
          className="flex flex-1 flex-wrap items-center justify-end gap-x-5 gap-y-2 sm:gap-x-8"
          aria-label="Основная навигация"
        >
          <Link href="/#about" className={navClass}>
            О приложении
          </Link>
          <Link href="/#download" className={navClass}>
            Скачать
          </Link>
          <Link
            href="/register"
            className={`${navClass} relative ${onRegister ? "text-sky-800" : ""}`}
          >
            Партнерам
            {onRegister ? (
              <span className="absolute -bottom-1 left-0 right-0 h-0.5 rounded-full bg-sky-500" />
            ) : null}
          </Link>
        </nav>
      </div>
    </header>
  );
}
