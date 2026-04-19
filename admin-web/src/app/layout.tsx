import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ChunkLoadRecovery } from "@/components/ChunkLoadRecovery";
import { CookieConsentBanner } from "@/components/legal/CookieConsentBanner";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "DiveHub",
  description:
    "Платформа для дайверов и дайв-бизнеса. Скачайте приложение и подайте заявку на подключение к каталогу.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  /* Inline-фон: если не подтянулся Tailwind/_next/static (nginx), не оставлять «белый экран». */
  const shellBg = "#09090b";
  return (
    <html lang="ru" className="bg-zinc-950" style={{ backgroundColor: shellBg }}>
      <body
        className={`${geistSans.variable} ${geistMono.variable} min-h-screen bg-zinc-950 text-zinc-100 antialiased`}
        style={{ backgroundColor: shellBg, margin: 0, minHeight: "100vh" }}
      >
        <ChunkLoadRecovery />
        {children}
        <CookieConsentBanner />
      </body>
    </html>
  );
}
