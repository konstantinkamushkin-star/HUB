import Link from "next/link";
import { BrandLogo } from "@/components/BrandLogo";

type Props = {
  title: string;
  children: React.ReactNode;
  backHref?: string;
};

export function LegalShell({
  title,
  children,
  backHref = "/",
}: Props) {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl flex-col gap-3 px-4 py-6 sm:px-6">
          <div className="flex items-center gap-3">
            <BrandLogo variant="mark" className="h-11 w-11 shrink-0" maskedMark />
            <div className="min-w-0 flex-1">
              <Link
                href={backHref}
                className="text-sm font-medium text-sky-600 hover:text-sky-700"
              >
                ← На главную
              </Link>
              <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
                {title}
              </h1>
            </div>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <article className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10 [&_h2]:mt-10 [&_h2]:scroll-mt-20 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:text-slate-900 [&_h2]:first:mt-0 [&_li]:mt-1 [&_ol]:list-decimal [&_ol]:space-y-1 [&_ol]:pl-5 [&_p]:leading-relaxed [&_ul]:list-disc [&_ul]:space-y-1 [&_ul]:pl-5">
          {children}
        </article>
      </main>
    </div>
  );
}
