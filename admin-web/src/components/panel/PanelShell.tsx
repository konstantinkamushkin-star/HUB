"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import { BrandLogo } from "@/components/BrandLogo";
import { NAV_ITEMS } from "./nav-items";
import { clearSession, getStoredUser } from "@/lib/auth";

export function PanelShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const user = getStoredUser();
  const [q, setQ] = useState("");

  const onSearch = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      const t = q.trim();
      if (t.length < 2) return;
      router.push(`/search?query=${encodeURIComponent(t)}`);
    },
    [q, router],
  );

  const groups = [...new Set(NAV_ITEMS.map((i) => i.group))];

  return (
    <div className="flex min-h-screen bg-zinc-950 text-zinc-100">
      <aside className="hidden w-56 shrink-0 flex-col border-r border-zinc-800 bg-zinc-900/80 md:flex">
        <div className="border-b border-zinc-800 px-4 py-4">
          <div className="flex items-center gap-3">
            <div className="shrink-0 rounded-lg bg-white px-2 py-1.5">
              <BrandLogo variant="wordmark" className="h-7 w-auto max-w-[160px]" />
            </div>
            <div>
              <div className="text-sm font-semibold text-zinc-100">Супер-админ</div>
            </div>
          </div>
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3">
          {groups.map((g) => (
            <div key={g} className="mb-4">
              <div className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                {g}
              </div>
              <ul className="space-y-0.5">
                {NAV_ITEMS.filter((i) => i.group === g).map((item) => {
                  const active =
                    pathname === item.href ||
                    (item.href !== "/dashboard" &&
                      pathname.startsWith(item.href));
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        className={`block rounded-md px-2 py-1.5 text-sm transition-colors ${
                          active
                            ? "bg-sky-600/20 text-sky-300"
                            : "text-zinc-300 hover:bg-zinc-800"
                        }`}
                      >
                        {item.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center gap-3 border-b border-zinc-800 bg-zinc-900/40 px-4 py-3">
          <form onSubmit={onSearch} className="flex min-w-[200px] flex-1 gap-2">
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Глобальный поиск (от 2 символов)…"
              className="w-full max-w-xl rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm outline-none focus:border-sky-600"
            />
            <button
              type="submit"
              className="rounded-md bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500"
            >
              Найти
            </button>
          </form>
          <div className="ml-auto flex items-center gap-3 text-sm text-zinc-400">
            {user && (
              <span className="hidden sm:inline">
                {user.email}{" "}
                <span className="text-zinc-600">·</span> {user.role}
              </span>
            )}
            <button
              type="button"
              onClick={() => {
                clearSession();
                router.replace("/login");
              }}
              className="rounded-md border border-zinc-700 px-3 py-1.5 hover:bg-zinc-800"
            >
              Выйти
            </button>
          </div>
        </header>

        <main className="flex-1 overflow-auto p-6">{children}</main>
        <footer className="shrink-0 border-t border-zinc-800 px-6 py-2 text-[10px] text-zinc-600">
          Сборка: {process.env.NEXT_PUBLIC_GIT_SHA ?? "—"} · при проблемах откройте{" "}
          <a href="/api/deploy-info" className="text-zinc-500 underline">
            /api/deploy-info
          </a>
        </footer>
      </div>
    </div>
  );
}
