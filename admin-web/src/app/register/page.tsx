import { PartnerRegistrationForm } from "@/components/landing/PartnerRegistrationForm";
import { PublicBackdrop } from "@/components/landing/PublicBackdrop";
import { PublicFooter } from "@/components/landing/PublicFooter";
import { PublicHeader } from "@/components/landing/PublicHeader";

export default function RegisterPage() {
  return (
    <div className="relative min-h-screen bg-gradient-to-b from-slate-50 via-sky-50/50 to-white text-slate-800">
      <PublicBackdrop />
      <PublicHeader />

      <main className="relative z-10">
        <section className="mx-auto max-w-3xl px-4 pb-16 pt-10 sm:px-6 sm:pb-24 sm:pt-14">
          <div className="rounded-3xl border border-sky-100/90 bg-white/95 p-6 shadow-xl shadow-sky-900/[0.08] sm:p-8">
            <PartnerRegistrationForm showIntro appearance="light" />
          </div>
        </section>
      </main>

      <PublicFooter />
    </div>
  );
}
