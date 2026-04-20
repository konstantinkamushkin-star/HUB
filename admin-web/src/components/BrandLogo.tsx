"use client";

import { useState } from "react";

const MARK_SRC = "/branding/logo-mark.png";
const WORDMARK_SRC = "/branding/logo-wordmark.png";

type Props = {
  /** Квадратная иконка или горизонтальный логотип с текстом DIVEHUB */
  variant?: "mark" | "wordmark";
  /** Напр. mark: `h-8 w-8`, wordmark: `h-8 w-auto max-w-[200px]` */
  className?: string;
  /** Скругление как у иконки приложения (только variant=mark) */
  maskedMark?: boolean;
};

export function BrandLogo({
  variant = "wordmark",
  className,
  maskedMark = false,
}: Props) {
  const [useFallback, setUseFallback] = useState(false);
  const SRC = variant === "wordmark" ? WORDMARK_SRC : MARK_SRC;
  const defaultClass =
    variant === "wordmark" ? "h-9 w-auto max-w-[220px]" : "h-8 w-8";

  if (useFallback) {
    const fbMask =
      variant === "mark" && maskedMark ? "rounded-[22%] bg-sky-500" : "";
    return (
      <span
        className={`inline-flex items-center justify-center ${fbMask} ${className ?? defaultClass}`}
        aria-hidden
      >
        <span className="text-xl leading-none">🤿</span>
      </span>
    );
  }

  const maskClass =
    variant === "mark" && maskedMark
      ? "rounded-[22%] shadow-sm ring-1 ring-black/5"
      : "";

  return (
    // eslint-disable-next-line @next/next/no-img-element -- локальный брендинг
    <img
      src={SRC}
      alt="DiveHub"
      className={`object-contain object-left ${maskClass} ${className ?? defaultClass}`}
      width={variant === "wordmark" ? undefined : 32}
      height={variant === "wordmark" ? undefined : 32}
      decoding="async"
      onError={() => setUseFallback(true)}
    />
  );
}
