"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { BrandLogo } from "@/components/BrandLogo";
import { apiLogin } from "@/lib/api";
import {
  canAccessAdminPanel,
  getToken,
  setSession,
  type StoredAdminUser,
} from "@/lib/auth";

export default function AdminConsoleLoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (getToken()) router.replace("/dashboard");
  }, [router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    const res = await apiLogin(email.trim(), password);
    setLoading(false);
    if (!res.ok || !res.data?.accessToken || !res.data.user) {
      setError(res.errorMessage ?? "Не удалось войти");
      return;
    }
    const u = res.data.user;
    if (!canAccessAdminPanel(u.role)) {
      setError("Нет роли администратора панели");
      return;
    }
    const stored: StoredAdminUser = {
      id: u.id,
      email: u.email,
      role: u.role,
      firstName: u.firstName,
      lastName: u.lastName,
    };
    setSession(res.data.accessToken, stored, res.data.refreshToken);
    router.replace("/dashboard");
  }

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center bg-zinc-950 px-4"
      style={{
        minHeight: "100vh",
        backgroundColor: "#09090b",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      <div className="w-full max-w-sm rounded-xl border border-zinc-800 bg-zinc-900/60 p-8 shadow-xl">
        <div className="flex flex-col items-center gap-3">
          <BrandLogo variant="mark" className="h-14 w-14" maskedMark />
          <div className="rounded-xl bg-white px-4 py-2">
            <BrandLogo variant="wordmark" className="h-8 w-auto max-w-[200px]" />
          </div>
        </div>
        <h1 className="mt-4 text-center text-lg font-semibold text-white">
          Админ-панель
        </h1>
        <p className="mt-1 text-center text-xs text-zinc-500">
          Вход для ролей SUPER_ADMIN, ADMIN, MODERATOR и др.
        </p>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-zinc-400">
              Email
            </label>
            <input
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="mt-1 w-full rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none focus:border-sky-600"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-zinc-400">
              Пароль
            </label>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="mt-1 w-full rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none focus:border-sky-600"
            />
          </div>
          {error ? (
            <p className="text-sm text-red-400" role="alert">
              {error}
            </p>
          ) : null}
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-md bg-sky-600 py-2.5 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
          >
            {loading ? "Вход…" : "Войти"}
          </button>
        </form>
        <p className="mt-4 text-center text-xs text-zinc-500">
          Нужно подключить дайв-центр или магазин?{" "}
          <Link href="/register-business" className="text-sky-500 hover:text-sky-400">
            Форма регистрации
          </Link>
        </p>
        <p className="mt-3 flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-center text-xs text-zinc-500">
          <Link href="/privacy" className="text-zinc-400 hover:text-zinc-200">
            Конфиденциальность
          </Link>
          <span aria-hidden className="text-zinc-700">
            ·
          </span>
          <Link href="/agreement" className="text-zinc-400 hover:text-zinc-200">
            Пользовательское соглашение
          </Link>
        </p>
      </div>
    </div>
  );
}
