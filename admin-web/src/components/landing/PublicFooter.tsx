import Link from "next/link";

export function PublicFooter() {
  const linkClass =
    "text-sm text-slate-600 transition-colors hover:text-sky-700";

  return (
    <footer className="relative z-20 border-t border-sky-100/80 bg-white/85 py-10 backdrop-blur-md">
      <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-6 px-4 text-center sm:flex-row sm:px-6 sm:text-left">
        <p className="text-sm font-medium text-slate-700">
          © {new Date().getFullYear()} DiveHub
        </p>
        <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
          <Link href="/register" className={linkClass}>
            Анкета партнера
          </Link>
          <Link href="/#download" className={linkClass}>
            Скачать приложение
          </Link>
        </div>
      </div>
    </footer>
  );
}
