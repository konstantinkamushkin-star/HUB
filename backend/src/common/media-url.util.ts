export function normalizeStoredMediaUrl(url: string): string {
  const trimmed = (url ?? '').trim();
  if (!trimmed) {
    return trimmed;
  }
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      return new URL(trimmed).pathname;
    } catch {
      return trimmed;
    }
  }
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

export function normalizeStoredMediaUrls(urls?: string[] | null): string[] {
  return (urls ?? []).map(normalizeStoredMediaUrl).filter((u) => u.length > 0);
}
