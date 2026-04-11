"use client";

import Link from "next/link";
import { PartnerRegistrationForm } from "@/components/landing/PartnerRegistrationForm";

export default function RegisterBusinessPage() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-sky-50 to-cyan-50/80 px-4 py-10 text-slate-800">
      <div className="mx-auto max-w-lg">
        <Link
          href="/#registration"
          className="text-sm font-medium text-sky-600 hover:text-sky-700"
        >
          ← На главную
        </Link>
        <div className="mt-6 rounded-3xl border border-sky-100 bg-white p-6 shadow-xl shadow-sky-900/10 sm:p-8">
          <PartnerRegistrationForm showIntro appearance="light" />
        </div>
        <p className="mt-6 flex flex-wrap justify-center gap-x-4 gap-y-1 text-center text-xs text-slate-500">
          <Link href="/privacy" className="text-sky-600 hover:text-sky-700">
            Политика конфиденциальности
          </Link>
          <Link href="/agreement" className="text-sky-600 hover:text-sky-700">
            Пользовательское соглашение
          </Link>
        </p>
      </div>
    </div>
  );
}
