"use client";

import Image from "next/image";
import Link from "next/link";
import { BrandLogo } from "@/components/BrandLogo";
import { PartnerRegistrationForm } from "./PartnerRegistrationForm";

const appStoreUrl = process.env.NEXT_PUBLIC_APP_STORE_URL?.trim() || "";
const playStoreUrl = process.env.NEXT_PUBLIC_GOOGLE_PLAY_URL?.trim() || "";

/** Локальные фото из public/landing (оригиналы: Филиппины 2024, подводная съёмка). */
const imgs = {
  hero: "/landing/hero.jpg",
  feature1: "/landing/feature-1.jpg",
  feature2: "/landing/feature-2.jpg",
  feature3: "/landing/feature-3.jpg",
  download: "/landing/download.jpg",
} as const;

function StoreButtons() {
  const hasAppStore = Boolean(appStoreUrl);
  const hasPlay = Boolean(playStoreUrl);
  const btn =
    "inline-flex items-center justify-center gap-3 rounded-xl border border-sky-200 bg-white px-5 py-3 text-sm font-medium text-slate-800 shadow-sm transition hover:border-sky-400 hover:shadow-md";

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      {hasAppStore ? (
        <a
          href={appStoreUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={btn}
        >
          <svg
            className="h-8 w-8 shrink-0 text-slate-900"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden
          >
            <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
          </svg>
          <span className="text-left leading-tight">
            <span className="block text-[10px] font-normal uppercase tracking-wide text-slate-500">
              Загрузить в
            </span>
            App Store
          </span>
        </a>
      ) : (
        <span
          className="inline-flex items-center justify-center gap-2 rounded-xl border border-dashed border-sky-200 bg-sky-50/50 px-5 py-3 text-sm text-slate-500"
          title="Добавьте NEXT_PUBLIC_APP_STORE_URL в .env.local"
        >
          App Store (ссылка не задана)
        </span>
      )}
      {hasPlay ? (
        <a
          href={playStoreUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={btn}
        >
          <svg
            className="h-7 w-7 shrink-0 text-emerald-600"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden
          >
            <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 0 1-.61-.92V2.734a1 1 0 0 1 .609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.198l2.807 1.626a1 1 0 0 1 0 1.73l-2.808 1.626L15.206 12l2.492-2.491zM5.864 2.658L16.802 8.99l-2.303 2.303-8.635-8.635z" />
          </svg>
          <span className="text-left leading-tight">
            <span className="block text-[10px] font-normal uppercase tracking-wide text-slate-500">
              Доступно в
            </span>
            Google Play
          </span>
        </a>
      ) : (
        <span
          className="inline-flex items-center justify-center gap-2 rounded-xl border border-dashed border-emerald-200 bg-emerald-50/50 px-5 py-3 text-sm text-slate-500"
          title="Добавьте NEXT_PUBLIC_GOOGLE_PLAY_URL в .env.local"
        >
          Google Play (ссылка не задана)
        </span>
      )}
    </div>
  );
}

const features: {
  title: string;
  text: string;
  emoji: string;
  image: string;
  alt: string;
}[] = [
  {
    title: "Карта и каталог",
    text: "Находите дайв-сайты и центры, планируйте погружения и открывайте новые локации.",
    emoji: "🗺️",
    image: imgs.feature1,
    alt: "Подводная съёмка, Филиппины",
  },
  {
    title: "Бронирования и логбук",
    text: "Записывайтесь на дайвинг, ведите дневник погружений и историю активности.",
    emoji: "🌊",
    image: imgs.feature2,
    alt: "Подводный мир",
  },
  {
    title: "Для бизнеса",
    text: "Дайв-центры и магазины подключаются к каталогу после верификации супер-админом.",
    emoji: "🤝",
    image: imgs.feature3,
    alt: "Дайвинг, Филиппины",
  },
];

export function LandingView() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-sky-50 via-cyan-50/80 to-white text-slate-800">
      <div className="pointer-events-none fixed inset-0 overflow-hidden">
        <div className="absolute -left-20 top-20 h-72 w-72 rounded-full bg-sky-300/40 blur-3xl" />
        <div className="absolute right-0 top-40 h-96 w-96 rounded-full bg-amber-200/35 blur-3xl" />
        <div className="absolute bottom-20 left-1/3 h-64 w-64 rounded-full bg-cyan-300/30 blur-3xl" />
      </div>

      <header className="relative z-10 border-b border-sky-100/80 bg-white/75 backdrop-blur-md">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
          <a href="#" className="flex items-center gap-2">
            <BrandLogo variant="wordmark" className="h-9 w-auto max-w-[200px] shrink-0 sm:h-10 sm:max-w-[240px]" />
          </a>
          <nav className="flex flex-1 flex-wrap items-center justify-end gap-x-4 gap-y-2 text-xs text-slate-600 sm:gap-6 sm:text-sm">
            <a href="#about" className="transition hover:text-sky-700">
              О приложении
            </a>
            <a href="#download" className="transition hover:text-sky-700">
              Скачать
            </a>
            <a href="#registration" className="transition hover:text-sky-700">
              Анкета
            </a>
          </nav>
          <Link
            href="/login"
            className="rounded-xl border border-sky-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-sky-400 hover:bg-sky-50"
          >
            Вход для админа
          </Link>
        </div>
      </header>

      <main className="relative z-10">
        <section className="mx-auto max-w-5xl px-4 pb-16 pt-12 sm:px-6 sm:pb-24 sm:pt-16">
          <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-12">
            <div>
              <p className="inline-flex items-center gap-2 rounded-full bg-sky-100/90 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-sky-800">
                <span aria-hidden>✨</span> DiveHub
              </p>
              <h1 className="mt-5 text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl sm:leading-[1.1]">
                Платформа для дайверов, инструкторов и дайв-центров
              </h1>
              <p className="mt-6 text-lg leading-relaxed text-slate-600">
                Откройте карту погружений, бронируйте выезды и ведите логбук — а
                для бизнеса подайте заявку на подключение к каталогу. После
                проверки супер-администратором карточка станет доступна в
                приложении.
              </p>
              <div className="mt-10 flex flex-col gap-4 sm:flex-row sm:items-center">
                <a
                  href="#download"
                  className="inline-flex items-center justify-center rounded-2xl bg-gradient-to-r from-sky-500 to-cyan-500 px-6 py-3.5 text-sm font-semibold text-white shadow-lg shadow-sky-500/30 transition hover:from-sky-400 hover:to-cyan-400"
                >
                  Скачать приложение
                </a>
                <a
                  href="#registration"
                  className="inline-flex items-center justify-center rounded-2xl border-2 border-sky-200 bg-white px-6 py-3.5 text-sm font-semibold text-sky-800 transition hover:border-sky-400 hover:bg-sky-50"
                >
                  Заявка для дайв-центра или магазина
                </a>
              </div>
            </div>
            <div className="relative">
              <div className="absolute -right-4 -top-4 h-24 w-24 rounded-3xl bg-amber-300/50 blur-2xl" />
              <div className="relative overflow-hidden rounded-3xl border-4 border-white shadow-2xl shadow-sky-900/15 ring-1 ring-sky-100">
                <Image
                  src={imgs.hero}
                  alt="Подводная съёмка, Филиппины 2024"
                  width={2000}
                  height={1500}
                  className="h-auto w-full object-cover"
                  sizes="(max-width: 1024px) 100vw, 50vw"
                  priority
                />
              </div>
              <p className="mt-3 text-center text-xs text-slate-500">
                Фото: Филиппины, 2024
              </p>
            </div>
          </div>
        </section>

        <section
          id="about"
          className="border-y border-sky-100/90 bg-white/60 py-16 backdrop-blur-sm sm:py-24"
        >
          <div className="mx-auto max-w-5xl px-4 sm:px-6">
            <h2 className="text-2xl font-bold text-slate-900 sm:text-3xl">
              Кратко о DiveHub
            </h2>
            <p className="mt-3 max-w-2xl text-lg text-slate-600">
              Мобильное приложение объединяет любителей подводного мира и
              профессиональные площадки: от поиска точек до бронирований и
              сообщества.
            </p>
            <ul className="mt-12 grid gap-8 sm:grid-cols-3">
              {features.map((f) => (
                <li
                  key={f.title}
                  className="group overflow-hidden rounded-3xl border border-sky-100 bg-white shadow-lg shadow-sky-900/5 transition hover:-translate-y-1 hover:shadow-xl"
                >
                  <div className="relative aspect-[4/3] overflow-hidden">
                    <Image
                      src={f.image}
                      alt={f.alt}
                      fill
                      className="object-cover transition duration-500 group-hover:scale-105"
                      sizes="(max-width: 640px) 100vw, 33vw"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-slate-900/50 to-transparent" />
                    <span className="absolute bottom-3 left-3 text-2xl drop-shadow-md">
                      {f.emoji}
                    </span>
                  </div>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-slate-900">
                      {f.title}
                    </h3>
                    <p className="mt-2 text-sm leading-relaxed text-slate-600">
                      {f.text}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="download" className="py-16 sm:py-24">
          <div className="mx-auto max-w-5xl px-4 sm:px-6">
            <div className="overflow-hidden rounded-3xl border border-sky-100 bg-gradient-to-br from-sky-100/80 to-cyan-100/60 shadow-xl shadow-sky-900/10">
              <div className="grid gap-0 lg:grid-cols-2">
                <div className="relative min-h-[220px] lg:min-h-[320px]">
                  <Image
                    src={imgs.download}
                    alt="Подводная съёмка для блока «Скачать»"
                    fill
                    className="object-cover"
                    sizes="(max-width: 1024px) 100vw, 50vw"
                  />
                </div>
                <div className="flex flex-col justify-center p-8 sm:p-10">
                  <h2 className="text-2xl font-bold text-slate-900 sm:text-3xl">
                    Скачать приложение
                  </h2>
                  <p className="mt-3 text-slate-600">
                    Установите DiveHub на iPhone или Android. Ссылки на магазины
                    задаются в переменных окружения для продакшена.
                  </p>
                  <div className="mt-8">
                    <StoreButtons />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section
          id="registration"
          className="border-t border-sky-100 bg-gradient-to-b from-white to-sky-50/80 py-16 sm:py-24"
        >
          <div className="mx-auto max-w-lg px-4 sm:px-6">
            <div className="rounded-3xl border border-sky-100 bg-white p-6 shadow-xl shadow-sky-900/10 sm:p-8">
              <PartnerRegistrationForm showIntro appearance="light" />
            </div>
            <p className="mt-6 text-center text-sm text-slate-500">
              Вход для администраторов или подача заявки на подключение
              дайв-центра или магазина. После заполнения формы заявка попадает в
              очередь верификации — её обработает супер-администратор.
            </p>
          </div>
        </section>
      </main>

      <footer className="relative z-10 border-t border-sky-100 bg-white/80 py-10 backdrop-blur-sm">
        <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-6 px-4 text-center text-sm text-slate-600 sm:flex-row sm:text-left sm:px-6">
          <p className="font-medium text-slate-700">
            © {new Date().getFullYear()} DiveHub
          </p>
          <div className="flex flex-wrap items-center justify-center gap-4">
            <Link
              href="/login"
              className="font-medium text-sky-600 hover:text-sky-700"
            >
              Супер-админ: войти в панель
            </Link>
            <span className="hidden text-slate-300 sm:inline" aria-hidden>
              |
            </span>
            <a href="#registration" className="hover:text-slate-900">
              Анкета партнёра
            </a>
            <span className="hidden text-slate-300 sm:inline" aria-hidden>
              |
            </span>
            <Link
              href="/privacy"
              className="hover:text-slate-900"
            >
              Конфиденциальность
            </Link>
            <Link
              href="/agreement"
              className="hover:text-slate-900"
            >
              Соглашение
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
