import type { Metadata } from "next";
import Image from "next/image";

type Lang = "ru" | "en";

const COPY: Record<Lang, { title: string; subtitle: string; labels: Record<string, string> }> = {
  ru: {
    title: "DiveHub Presentation",
    subtitle: "Актуальные сценарии приложения",
    labels: {
      search: "Поиск",
      booking: "Бронирование",
      logbook: "Логбук",
      support: "Поддержка",
    },
  },
  en: {
    title: "DiveHub Presentation",
    subtitle: "Live app scenarios",
    labels: {
      search: "Search",
      booking: "Booking",
      logbook: "Logbook",
      support: "Support",
    },
  },
};

const SCENARIOS = [
  { key: "search", fileBase: "scenario-search" },
  { key: "booking", fileBase: "scenario-booking" },
  { key: "logbook", fileBase: "scenario-logbook" },
  { key: "support", fileBase: "scenario-support" },
] as const;

export const metadata: Metadata = {
  title: "Presentation — DiveHub",
  description: "DiveHub app presentation scenarios",
};

function pickLang(raw: string | undefined): Lang {
  return raw === "en" ? "en" : "ru";
}

export default async function PresentationPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const lang = pickLang(typeof params.lang === "string" ? params.lang : undefined);
  const copy = COPY[lang];

  return (
    <main
      style={{
        minHeight: "100vh",
        background: "#020617",
        color: "#f8fafc",
        fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, sans-serif",
        padding: "32px 20px 48px",
      }}
    >
      <div style={{ margin: "0 auto", maxWidth: 1280 }}>
        <h1 style={{ fontSize: 36, fontWeight: 700, margin: 0 }}>{copy.title}</h1>
        <p style={{ marginTop: 8, color: "#94a3b8" }}>{copy.subtitle}</p>
        <div
          style={{
            display: "grid",
            gap: 20,
            marginTop: 24,
            gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
          }}
        >
          {SCENARIOS.map((scenario) => (
            <figure
              key={scenario.key}
              style={{
                margin: 0,
                borderRadius: 18,
                border: "1px solid rgba(148,163,184,0.25)",
                background: "#0b1220",
                padding: 12,
              }}
            >
              <Image
                src={`/presentation/${scenario.fileBase}.png`}
                alt={copy.labels[scenario.key]}
                width={1170}
                height={2532}
                style={{
                  width: "100%",
                  height: "auto",
                  aspectRatio: "1170 / 2532",
                  borderRadius: 12,
                  objectFit: "cover",
                  background: "#111827",
                }}
              />
              <figcaption style={{ marginTop: 10, fontSize: 14, color: "#cbd5e1" }}>
                {copy.labels[scenario.key]}
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </main>
  );
}
