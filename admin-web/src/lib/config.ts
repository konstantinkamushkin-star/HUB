export function getApiBaseUrl(): string {
  const raw =
    process.env.NEXT_PUBLIC_API_URL ?? "https://api.dive-hub.ru";
  return raw.replace(/\/$/, "");
}
