"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ADMIN_LOGIN_PATH } from "@/lib/adminLoginPath";
import { getToken } from "@/lib/auth";

export function AuthGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken()) {
      router.replace(ADMIN_LOGIN_PATH);
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return (
      <div
        className="flex min-h-screen items-center justify-center bg-zinc-950 text-zinc-400"
        style={{
          minHeight: "100vh",
          backgroundColor: "#09090b",
          color: "#a1a1aa",
          fontFamily: "system-ui, sans-serif",
        }}
      >
        Загрузка…
      </div>
    );
  }

  return <>{children}</>;
}
