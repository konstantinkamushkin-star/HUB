import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { ChunkLoadRecovery } from "@/components/ChunkLoadRecovery";
import { CookieConsentBanner } from "@/components/legal/CookieConsentBanner";
import { brandingLogoUrl } from "@/lib/branding";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin", "cyrillic"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "DiveHub",
  description:
    "Платформа для дайверов и дайв-бизнеса. Скачайте приложение и подайте заявку на подключение к каталогу.",
  icons: {
    icon: [{ url: brandingLogoUrl, type: "image/png" }],
    shortcut: brandingLogoUrl,
    apple: brandingLogoUrl,
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru" className="scroll-smooth">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <ChunkLoadRecovery />
        {children}
        <CookieConsentBanner />
      </body>
    </html>
  );
}
