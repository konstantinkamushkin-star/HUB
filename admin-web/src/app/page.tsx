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

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-sky-50 text-slate-500">
        Загрузка…
      </div>
    );
  }

  return <LandingView />;
}
