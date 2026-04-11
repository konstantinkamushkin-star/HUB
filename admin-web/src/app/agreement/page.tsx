import type { Metadata } from "next";
import { LegalShell } from "@/components/legal/LegalShell";
import { DiveHubAgreementContent } from "@/components/legal/DiveHubAgreementContent";

export const metadata: Metadata = {
  title: "Пользовательское соглашение — DiveHub",
  description:
    "Условия использования сервиса DiveHub. Оператор: ИП Попов-Толмачёв Д. Б.",
};

export default function AgreementPage() {
  return (
    <LegalShell title="Пользовательское соглашение DiveHub">
      <DiveHubAgreementContent />
    </LegalShell>
  );
}
