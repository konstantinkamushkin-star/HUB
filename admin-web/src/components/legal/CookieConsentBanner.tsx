"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

const STORAGE_KEY = "divehub_cookie_consent_v1";

type CookieConsent = {
  essential: true;
  analytics: boolean;
  marketing: boolean;
  updatedAt: string;
};

function writeConsentCookie(value: CookieConsent) {
  const encoded = encodeURIComponent(JSON.stringify(value));
  const maxAge = 60 * 60 * 24 * 365;
  document.cookie = `divehub_cookie_consent=${encoded}; Max-Age=${maxAge}; Path=/; SameSite=Lax; Secure`;
}

export function CookieConsentBanner() {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const raw = localStorage.getItem(STORAGE_KEY);
    setIsVisible(!raw);
  }, []);

  const baseConsent = useMemo(
    () => ({
      essential: true as const,
      updatedAt: new Date().toISOString(),
    }),
    [],
  );

  function save(consent: CookieConsent) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(consent));
    writeConsentCookie(consent);
    setIsVisible(false);
  }

  if (!isVisible) return null;

  return (
    <div className="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-5xl flex-col gap-3 px-4 py-4 text-sm text-slate-700 md:flex-row md:items-center md:justify-between">
        <p className="leading-relaxed">
          Мы используем обязательные cookie для работы сайта и, с вашего согласия,
          аналитические cookie для улучшения сервиса. Подробнее в{" "}
          <Link href="/privacy" className="text-sky-700 underline hover:text-sky-800">
            Политике конфиденциальности
          </Link>
          .
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded-md border border-slate-300 px-3 py-1.5 text-slate-700 hover:bg-slate-100"
            onClick={() =>
              save({
                ...baseConsent,
                analytics: false,
                marketing: false,
              })
            }
          >
            Только обязательные
          </button>
          <button
            type="button"
            className="rounded-md bg-sky-700 px-3 py-1.5 text-white hover:bg-sky-800"
            onClick={() =>
              save({
                ...baseConsent,
                analytics: true,
                marketing: false,
              })
            }
          >
            Принять
          </button>
        </div>
      </div>
    </div>
  );
}
