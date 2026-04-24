import type { NextConfig } from "next";
import { execSync } from "node:child_process";

/** Видно в панели — чтобы убедиться, что на проде именно эта сборка (не старый кэш / другой каталог). */
function gitShortSha(): string {
  try {
    return execSync("git rev-parse --short HEAD", {
      encoding: "utf8",
      cwd: process.cwd(),
    }).trim();
  } catch {
    return "unknown";
  }
}

/** Прокси на Nest: app/api-proxy/[...path]/route.ts (JSON-ошибки при недоступном backend). */
const nextConfig: NextConfig = {
  env: {
    NEXT_PUBLIC_GIT_SHA: gitShortSha(),
  },
  async rewrites() {
    return {
      beforeFiles: [
        { source: "/presentation", destination: "/presentation-page" },
      ],
    };
  },
};

export default nextConfig;
