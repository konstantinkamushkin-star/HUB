"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { LandingView } from "@/components/landing/LandingView";
import { getToken } from "@/lib/auth";

export default function Home() {
  const router = useRouter();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    if (getToken()) {
      router.replace("/dashboard");
      return;
    }
    setChecking(false);
  }, [router]);

  /* Инлайн-фон: без CSS (битые чанки / кэш) иначе «белый экран» */
  const shellBg = "#09090b";
  const shellFg = "#a1a1aa";

  if (checking) {
    return (
      <div
        className="flex min-h-screen items-center justify-center"
        style={{
          minHeight: "100vh",
          backgroundColor: shellBg,
          color: shellFg,
          fontFamily: "system-ui, sans-serif",
        }}
      >
        Загрузка…
      </div>
    );
  }

  return <LandingView />;
}
