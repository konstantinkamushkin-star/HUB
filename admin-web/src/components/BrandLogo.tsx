"use client";

import { useState } from "react";
import { brandingLogoUrl } from "@/lib/branding";

const SRC = brandingLogoUrl;

type Props = {
  /** Размер и отступы, напр. `h-8 w-8` или `h-9 w-9` */
  className?: string;
  /** Совместимость со старыми вызовами компонента. */
  variant?: "mark" | "wordmark";
  maskedMark?: boolean;
};

/** Логотип бренда: файл `public/branding/logo.svg`. */
export function BrandLogo({ className = "h-8 w-8" }: Props) {
  const [useFallback, setUseFallback] = useState(false);

  if (useFallback) {
    return (
      <span className={`inline-flex items-center justify-center ${className}`} aria-hidden>
        <span className="text-xl leading-none">🤿</span>
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element -- локальный брендинг, заменяемый файл
    <img
      src={SRC}
      alt="DiveHub"
      className={`object-contain ${className}`}
      width={581}
      height={581}
      decoding="async"
      onError={() => setUseFallback(true)}
    />
  );
}
