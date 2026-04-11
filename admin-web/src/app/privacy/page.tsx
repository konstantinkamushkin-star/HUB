import type { Metadata } from "next";
import { LegalShell } from "@/components/legal/LegalShell";
import { DiveHubPrivacyContent } from "@/components/legal/DiveHubPrivacyContent";

export const metadata: Metadata = {
  title: "Политика конфиденциальности — DiveHub",
  description:
    "Политика обработки персональных данных сервиса DiveHub. Оператор: ИП Попов-Толмачёв Д. Б.",
};

export default function PrivacyPage() {
  return (
    <LegalShell title="Политика конфиденциальности DiveHub">
      <DiveHubPrivacyContent />
    </LegalShell>
  );
}
