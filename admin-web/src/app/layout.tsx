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
  icons: {
    icon: [
      { url: "/branding/favicon-32x32.png", sizes: "32x32", type: "image/png" },
      { url: "/branding/favicon-16x16.png", sizes: "16x16", type: "image/png" },
      { url: "/branding/logo.svg", type: "image/svg+xml" },
    ],
    shortcut: "/favicon.ico",
    apple: [{ url: "/branding/apple-touch-icon.png", sizes: "180x180" }],
    other: [
      {
        rel: "mask-icon",
        url: "/branding/logo.svg",
        color: "#0ea5e9",
      },
    ],
  },
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
