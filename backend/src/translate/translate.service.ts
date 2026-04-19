import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

/**
 * LibreTranslate (LIBRETRANSLATE_URL) приоритетнее Google (GOOGLE_TRANSLATE_API_KEY).
 * Иначе — возврат исходного текста.
 */
@Injectable()
export class TranslateService {
  private readonly logger = new Logger(TranslateService.name);

  constructor(private readonly config: ConfigService) {}

  async translate(
    text: string,
    sourceLanguage: string,
    targetLanguage: string,
  ): Promise<string> {
    const t = text?.trim() ?? '';
    if (!t) return '';
    const src = sourceLanguage.trim().toLowerCase();
    const tgt = targetLanguage.trim().toLowerCase();
    if (src === tgt) return text;

    const libre = this.getLibreBaseUrl();
    if (libre) {
      try {
        return await this.translateLibre(t, src, tgt, libre);
      } catch (e) {
        this.logger.warn(
          `LibreTranslate failed: ${e instanceof Error ? e.message : String(e)}`,
        );
        const googleKey = this.config.get<string>('GOOGLE_TRANSLATE_API_KEY')?.trim();
        if (googleKey) {
          return this.translateGoogle(t, src, tgt, googleKey);
        }
        return text;
      }
    }

    const key = this.config.get<string>('GOOGLE_TRANSLATE_API_KEY')?.trim();
    if (!key) return text;
    return this.translateGoogle(t, src, tgt, key);
  }

  async translateBatch(
    texts: string[],
    sourceLanguage: string,
    targetLanguage: string,
  ): Promise<string[]> {
    if (!texts.length) return [];
    const src = sourceLanguage.trim().toLowerCase();
    const tgt = targetLanguage.trim().toLowerCase();
    if (src === tgt) return texts;

    const libre = this.getLibreBaseUrl();
    if (libre) {
      try {
        return Promise.all(
          texts.map((x) =>
            x?.trim()
              ? this.translateLibre(x, src, tgt, libre)
              : Promise.resolve(x ?? ''),
          ),
        );
      } catch (e) {
        this.logger.warn(
          `LibreTranslate batch failed: ${e instanceof Error ? e.message : String(e)}`,
        );
        const googleKey = this.config.get<string>('GOOGLE_TRANSLATE_API_KEY')?.trim();
        if (googleKey) {
          return this.translateBatchGoogle(texts, src, tgt, googleKey);
        }
        return texts;
      }
    }

    const key = this.config.get<string>('GOOGLE_TRANSLATE_API_KEY')?.trim();
    if (!key) return texts;
    return this.translateBatchGoogle(texts, src, tgt, key);
  }

  private getLibreBaseUrl(): string | null {
    const u = this.config.get<string>('LIBRETRANSLATE_URL')?.trim();
    if (!u) return null;
    return u.replace(/\/+$/, '');
  }

  private librePayload(extra: Record<string, unknown>): Record<string, unknown> {
    const apiKey = this.config.get<string>('LIBRETRANSLATE_API_KEY')?.trim();
    if (apiKey) {
      return { ...extra, api_key: apiKey };
    }
    return extra;
  }

  private async libreResolveSource(
    text: string,
    base: string,
    sourceLanguage: string,
  ): Promise<string> {
    if (sourceLanguage !== 'auto') {
      return sourceLanguage;
    }
    const res = await fetch(`${base}/detect`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(this.librePayload({ q: text })),
    });
    if (!res.ok) {
      const err = await res.text();
      throw new Error(`LibreTranslate detect ${res.status}: ${err}`);
    }
    const rows = (await res.json()) as
      | { language?: string; confidence?: number }[]
      | null;
    const lang = Array.isArray(rows) ? rows[0]?.language : undefined;
    return typeof lang === 'string' && lang ? lang : 'en';
  }

  private async translateLibre(
    text: string,
    sourceLanguage: string,
    targetLanguage: string,
    base: string,
  ): Promise<string> {
    const source = await this.libreResolveSource(text, base, sourceLanguage);
    const res = await fetch(`${base}/translate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(
        this.librePayload({
          q: text,
          source,
          target: targetLanguage,
          format: 'text',
        }),
      ),
    });
    if (!res.ok) {
      const err = await res.text();
      throw new Error(`LibreTranslate ${res.status}: ${err}`);
    }
    const data = (await res.json()) as { translatedText?: string };
    return data?.translatedText ?? text;
  }

  private async translateGoogle(
    text: string,
    sourceLanguage: string,
    targetLanguage: string,
    key: string,
  ): Promise<string> {
    const url = `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(key)}`;
    const body: Record<string, unknown> = {
      q: text,
      target: targetLanguage,
      format: 'text',
    };
    if (sourceLanguage !== 'auto') {
      body.source = sourceLanguage;
    }
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const err = await res.text();
      throw new Error(`Google Translate failed: ${res.status} ${err}`);
    }
    const data = (await res.json()) as {
      data?: { translations?: { translatedText?: string }[] };
    };
    return data?.data?.translations?.[0]?.translatedText ?? text;
  }

  private async translateBatchGoogle(
    texts: string[],
    sourceLanguage: string,
    targetLanguage: string,
    key: string,
  ): Promise<string[]> {
    const url = `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(key)}`;
    const body: Record<string, unknown> = {
      q: texts,
      target: targetLanguage,
      format: 'text',
    };
    if (sourceLanguage !== 'auto') {
      body.source = sourceLanguage;
    }
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const err = await res.text();
      throw new Error(`Google Translate batch failed: ${res.status} ${err}`);
    }
    const data = (await res.json()) as {
      data?: { translations?: { translatedText?: string }[] };
    };
    const out = data?.data?.translations?.map((x) => x.translatedText ?? '');
    if (!out || out.length !== texts.length) {
      return texts;
    }
    return out;
  }
}
