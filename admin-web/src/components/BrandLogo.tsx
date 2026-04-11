"use client";

import { useState } from "react";

const MARK_SRC = "/branding/logo-mark.png";
const WORDMARK_SRC = "/branding/logo-wordmark.png";

type Props = {
  /** Квадратная иконка или горизонтальный логотип с текстом DIVEHUB */
  variant?: "mark" | "wordmark";
  /** Напр. mark: `h-8 w-8`, wordmark: `h-8 w-auto max-w-[200px]` */
  className?: string;
};

export function BrandLogo({ variant = "wordmark", className }: Props) {
  const [useFallback, setUseFallback] = useState(false);
  const SRC = variant === "wordmark" ? WORDMARK_SRC : MARK_SRC;
  const defaultClass =
    variant === "wordmark" ? "h-9 w-auto max-w-[220px]" : "h-8 w-8";

  if (useFallback) {
    return (
      <span
        className={`inline-flex items-center justify-center ${className ?? defaultClass}`}
        aria-hidden
      >
        <span className="text-xl leading-none">🤿</span>
      </span>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element -- локальный брендинг
    <img
      src={SRC}
      alt="DiveHub"
      className={`object-contain object-left ${className ?? defaultClass}`}
      width={variant === "wordmark" ? undefined : 32}
      height={variant === "wordmark" ? undefined : 32}
      decoding="async"
      onError={() => setUseFallback(true)}
    />
  );
}
