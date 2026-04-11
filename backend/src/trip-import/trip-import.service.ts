import {
  BadRequestException,
  Injectable,
  Logger,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosError } from 'axios';
import * as cheerio from 'cheerio';
import { MediaService } from '../media/media.service';
import { TripsWriteService } from '../trips/trips-write.service';

const FETCH_TIMEOUT_MS = 35_000;
const USER_AGENT =
  'Mozilla/5.0 (compatible; DiveHubTripBot/1.0; +https://divehub.app)';

type LlmTripShape = {
  title?: string;
  description?: string;
  country?: string;
  region?: string | null;
  tripType?: string;
  startDate?: string;
  endDate?: string;
  totalSpots?: number;
  minimumCertificationLevel?: string | null;
  minimumDives?: number | null;
  nitroxAvailable?: boolean;
  equipmentRentalAvailable?: boolean;
  divingPrice?: number | null;
  nonDivingPrice?: number | null;
  currency?: string;
  /** Индексы в переданном списке candidate image URLs */
  imagePickIndexes?: number[];
};

type ParsedExpense = {
  id: string;
  expenseType: 'flight' | 'transfer' | 'nutrition' | 'reserve' | 'other';
  description: string;
  cost: number;
  currency: string;
};

type PriceExtraction = {
  currency: string;
  divingPrice: number | null;
  nonDivingPrice: number | null;
  additionalExpenses: ParsedExpense[];
};

function assertFetchableUrl(raw: string): URL {
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new BadRequestException('Некорректный URL');
  }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') {
    throw new BadRequestException('Разрешены только http/https');
  }
  const host = u.hostname.toLowerCase();
  if (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '0.0.0.0' ||
    host.endsWith('.localhost')
  ) {
    throw new BadRequestException('Запрещённый хост');
  }
  if (
    /^10\./.test(host) ||
    /^192\.168\./.test(host) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(host)
  ) {
    throw new BadRequestException('Запрещены частные адреса');
  }
  return u;
}

function resolveUrl(base: string, href: string): string | null {
  try {
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

function extFromMime(mime: string | undefined, fallbackUrl: string): string {
  const m = (mime || '').toLowerCase();
  if (m.includes('png')) return '.png';
  if (m.includes('webp')) return '.webp';
  if (m.includes('jpeg') || m.includes('jpg')) return '.jpg';
  const u = fallbackUrl.toLowerCase();
  if (u.endsWith('.png')) return '.png';
  if (u.endsWith('.webp')) return '.webp';
  return '.jpg';
}

const RU_MONTHS: Record<string, number> = {
  января: 1,
  февраля: 2,
  марта: 3,
  апреля: 4,
  мая: 5,
  июня: 6,
  июля: 7,
  августа: 8,
  сентября: 9,
  октября: 10,
  ноября: 11,
  декабря: 12,
};

/** Транслит в slug (so-2-po-9-maya) */
const RU_MONTH_SLUG: Record<string, number> = {
  yanvarya: 1,
  fevralya: 2,
  marta: 3,
  aprelya: 4,
  maya: 5,
  iyunya: 6,
  iyulya: 7,
  avgusta: 8,
  sentyabrya: 9,
  oktyabrya: 10,
  noyabrya: 11,
  dekabrya: 12,
};

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function toIsoDate(y: number, m: number, d: number): string {
  return `${y}-${pad2(m)}-${pad2(d)}`;
}

function findIsoDatesInString(s: string): string[] {
  const m = s.match(/\b(\d{4}-\d{2}-\d{2})\b/g);
  return m || [];
}

function parseRussianRangeInText(s: string, defaultYear: number): { start?: string; end?: string } {
  const reSoPo =
    /с\s+(\d{1,2})\s+по\s+(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\s+(\d{4}))?/iu;
  const reDash =
    /(\d{1,2})\s*[-–]\s*(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\s+(\d{4}))?/iu;
  let m = reSoPo.exec(s);
  if (!m) m = reDash.exec(s);
  if (!m) return {};
  const d1 = parseInt(m[1], 10);
  const d2 = parseInt(m[2], 10);
  const monthName = m[3].toLowerCase();
  const month = RU_MONTHS[monthName];
  if (!month) return {};
  const year = m[4] ? parseInt(m[4], 10) : defaultYear;
  return {
    start: toIsoDate(year, month, d1),
    end: toIsoDate(year, month, d2),
  };
}

/** Парсит slug вида so-2-po-9-maya или so-2-po-9-maya-2026 */
function parseSlugDateRange(pageUrl: string, defaultYear: number): { start?: string; end?: string } {
  try {
    const path = new URL(pageUrl).pathname.toLowerCase();
    const re = /so-(\d+)-po-(\d+)-([a-z]+)(?:-(\d{4}))?/i;
    const m = re.exec(path);
    if (!m) return {};
    const d1 = parseInt(m[1], 10);
    const d2 = parseInt(m[2], 10);
    const month = RU_MONTH_SLUG[m[3]];
    if (!month) return {};
    const year = m[4] ? parseInt(m[4], 10) : defaultYear;
    return {
      start: toIsoDate(year, month, d1),
      end: toIsoDate(year, month, d2),
    };
  } catch {
    return {};
  }
}

/** DD.MM.YYYY / DD.MM.YY в тексте */
function parseDotDatesInText(s: string): { start?: string; end?: string } {
  const re = /\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b/g;
  const found: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(s)) !== null) {
    let y = parseInt(m[3], 10);
    if (y < 100) y += 2000;
    const mo = parseInt(m[2], 10);
    const d = parseInt(m[1], 10);
    if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
      found.push(toIsoDate(y, mo, d));
    }
  }
  if (found.length >= 2) {
    found.sort();
    return { start: found[0], end: found[found.length - 1] };
  }
  if (found.length === 1) return { start: found[0], end: found[0] };
  return {};
}

/** Одна дата: «9 мая 2025» */
function parseSingleRussianDateInText(s: string, defaultYear: number): string | undefined {
  const re =
    /(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\s+(\d{4})/iu;
  const m = re.exec(s);
  if (!m) return undefined;
  const d = parseInt(m[1], 10);
  const month = RU_MONTHS[m[2].toLowerCase()];
  if (!month) return undefined;
  const y = parseInt(m[3], 10);
  return toIsoDate(y, month, d);
}

type JsonLdTripHints = {
  start?: string;
  end?: string;
  title?: string;
  description?: string;
  country?: string;
  imageUrls?: string[];
};

function schemaIsoDate(v: unknown): string | undefined {
  if (typeof v !== 'string') return undefined;
  const m = v.match(/^(\d{4}-\d{2}-\d{2})/);
  return m ? m[1] : undefined;
}

function extractJsonLdTripHints(html: string): JsonLdTripHints {
  const out: JsonLdTripHints = {};
  const $ = cheerio.load(html);
  $('script[type="application/ld+json"]').each((_, el) => {
    const raw = $(el).html();
    if (!raw?.trim()) return;
    let data: unknown;
    try {
      data = JSON.parse(raw) as unknown;
    } catch {
      return;
    }
    const takeImage = (v: unknown): void => {
      if (typeof v === 'string') {
        if (!out.imageUrls) out.imageUrls = [];
        if (!out.imageUrls.includes(v)) out.imageUrls.push(v);
      } else if (v && typeof v === 'object' && !Array.isArray(v)) {
        const o = v as Record<string, unknown>;
        const u = o.url;
        if (typeof u === 'string') {
          if (!out.imageUrls) out.imageUrls = [];
          if (!out.imageUrls.includes(u)) out.imageUrls.push(u);
        }
      }
    };
    const walk = (obj: unknown): void => {
      if (!obj || typeof obj !== 'object') return;
      if (Array.isArray(obj)) {
        obj.forEach(walk);
        return;
      }
      const o = obj as Record<string, unknown>;
      const types = o['@type'];
      const typeStr =
        typeof types === 'string'
          ? types
          : Array.isArray(types)
            ? types.join(' ')
            : '';
      const interesting =
        /Event|TouristTrip|Trip|Product|Offer|TravelAction|LodgingReservation/i.test(
          typeStr,
        ) || typeStr.length === 0;

      if (interesting) {
        const sd = schemaIsoDate(o.startDate) || schemaIsoDate(o.validFrom);
        const ed = schemaIsoDate(o.endDate) || schemaIsoDate(o.validThrough);
        if (sd) out.start = sd;
        if (ed) out.end = ed;
        const name = o.name ?? o.headline;
        if (typeof name === 'string' && name.trim().length > 2 && !out.title) {
          out.title = name.trim();
        }
        const desc = o.description;
        if (typeof desc === 'string' && desc.trim().length > 20 && !out.description) {
          out.description = desc.trim();
        }
        const img = o.image;
        if (typeof img === 'string') takeImage(img);
        else if (Array.isArray(img)) img.forEach(takeImage);
        const loc = o.location ?? o.address;
        if (loc && typeof loc === 'object' && !Array.isArray(loc)) {
          const L = loc as Record<string, unknown>;
          const ac = L.addressCountry;
          if (typeof ac === 'string' && ac.trim().length > 1 && !out.country) {
            out.country = ac.trim();
          }
          const addr = L.address;
          if (addr && typeof addr === 'object' && !Array.isArray(addr)) {
            const ac2 = (addr as Record<string, unknown>).addressCountry;
            if (typeof ac2 === 'string' && ac2.trim().length > 1 && !out.country) {
              out.country = ac2.trim();
            }
          }
        }
      }
      const sd = o.startDate;
      const ed = o.endDate;
      if (typeof sd === 'string') {
        const iso = schemaIsoDate(sd);
        if (iso) out.start = iso;
      }
      if (typeof ed === 'string') {
        const iso = schemaIsoDate(ed);
        if (iso) out.end = iso;
      }
      Object.values(o).forEach(walk);
    };
    walk(data);
  });
  return out;
}

/** Русские/англ. подсказки по названию направления → страна на английском */
const COUNTRY_HINTS: { re: RegExp; country: string }[] = [
  { re: /шарм|дахаб|хургада|красн(ое|ым)\s+мор|египет|египетск/i, country: 'Egypt' },
  { re: /малайзи|сипадан|мабу/i, country: 'Malaysia' },
  { re: /индонези|бали|раджа\s*ампат|комодо/i, country: 'Indonesia' },
  { re: /тайланд|таиланд|симилан|пхи-пхи|пхукет/i, country: 'Thailand' },
  { re: /филиппин|малапаскуа|туббатаха/i, country: 'Philippines' },
  { re: /мальдив/i, country: 'Maldives' },
  { re: /турци|кемер|бодрум/i, country: 'Turkey' },
  { re: /мексик|козумел|сокорро/i, country: 'Mexico' },
  { re: /багам|бонэйр|кюрасао|аруба/i, country: 'Bahamas' },
  { re: /палау|трук|chuuk/i, country: 'Palau' },
  { re: /фиджи/i, country: 'Fiji' },
  { re: /судан|сангаанеб/i, country: 'Sudan' },
  { re: /джибути/i, country: 'Djibouti' },
  { re: /оман|мусандам/i, country: 'Oman' },
];

function inferCountryFromCorpus(s: string): string | undefined {
  const t = s.toLowerCase();
  for (const { re, country } of COUNTRY_HINTS) {
    if (re.test(t)) return country;
  }
  return undefined;
}

function isLikelyJunkImageUrl(abs: string): boolean {
  const low = abs.toLowerCase();
  if (low.includes('logo') || low.includes('favicon')) return true;
  if (/\/icons?\//i.test(low) || /sprite|spacer|blank\.|pixel\.|1x1/i.test(low)) return true;
  if (low.includes('avatar') || low.includes('gravatar') || low.includes('badge')) return true;
  if (/\b(w|h)=\d{1,3}\b/.test(low) && /[?&]w=\d{2,3}/.test(low)) return true;
  return false;
}

function scoreImageUrl(abs: string): number {
  if (isLikelyJunkImageUrl(abs)) return -1000;
  const low = abs.toLowerCase();
  let s = 0;
  if (/\.(jpe?g|webp|png)(\?|$)/i.test(low)) s += 5;
  if (low.includes('upload') || low.includes('wp-content') || low.includes('images')) s += 3;
  if (low.includes('thumb') || low.includes('thumbnail') || low.includes('_50.') || low.includes('_100.')) {
    s -= 8;
  }
  if (low.includes('large') || low.includes('full') || low.includes('1920')) s += 4;
  return s;
}

function pickImageIndexesByScore(imageCandidates: string[], max = 8): number[] {
  const scored = imageCandidates.map((u, i) => ({ i, s: scoreImageUrl(u) }));
  scored.sort((a, b) => b.s - a.s);
  return scored.filter((x) => x.s > -500).slice(0, max).map((x) => x.i);
}

function detectCurrencyFromText(text: string): string {
  const t = text.toLowerCase();
  if (/\b(usd|dollar|доллар)\b|[$]/i.test(t)) return 'USD';
  if (/\b(eur|euro|евро)\b|[€]/i.test(t)) return 'EUR';
  if (/\b(gbp|pound|фунт)\b|[£]/i.test(t)) return 'GBP';
  if (/\b(rub|руб|руб\.|₽)\b/i.test(t)) return 'RUB';
  return 'USD';
}

type MoneyHit = {
  value: number;
  currency: string;
  idx: number;
};

function parseMoneyValue(raw: string): number | null {
  let s = raw.trim();
  s = s.replace(/[^\d.,]/g, '');
  if (!s) return null;
  const hasComma = s.includes(',');
  const hasDot = s.includes('.');
  if (hasComma && hasDot) {
    if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
      s = s.replace(/\./g, '').replace(',', '.');
    } else {
      s = s.replace(/,/g, '');
    }
  } else if (hasComma) {
    s = s.replace(',', '.');
  }
  const n = Number(s);
  if (!Number.isFinite(n)) return null;
  if (n <= 0 || n > 1_000_000) return null;
  return Math.round(n * 100) / 100;
}

function detectCurrencyNear(token: string): string {
  const t = token.toLowerCase();
  if (t.includes('€') || /\beur|euro|евро\b/i.test(t)) return 'EUR';
  if (t.includes('$') || /\busd|dollar|доллар\b/i.test(t)) return 'USD';
  if (t.includes('£') || /\bgbp|pound|фунт\b/i.test(t)) return 'GBP';
  if (t.includes('₽') || /\brub|руб\b/i.test(t)) return 'RUB';
  return '';
}

function collectMoneyHits(text: string, defaultCurrency: string): MoneyHit[] {
  const out: MoneyHit[] = [];
  const re = /(?:[$€£₽]\s*\d[\d\s.,]*)|(?:\d[\d\s.,]*\s*(?:usd|eur|gbp|rub|dollars?|euro|pounds?|доллар(?:ов|а)?|евро|фунт(?:ов|а)?|руб(?:лей|ля|\.|)))/giu;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const token = m[0];
    const num = parseMoneyValue(token);
    if (num == null) continue;
    const c = detectCurrencyNear(token) || defaultCurrency;
    out.push({ value: num, currency: c, idx: m.index });
  }
  return out;
}

function inferExpenseType(line: string): ParsedExpense['expenseType'] {
  const t = line.toLowerCase();
  if (/перел[её]т|flight|aviat|airfare|ticket/i.test(t)) return 'flight';
  if (/трансфер|transfer|taxi|shuttle/i.test(t)) return 'transfer';
  if (/питан|еда|meal|food|breakfast|lunch|dinner/i.test(t)) return 'nutrition';
  if (/сбор|fee|port|park|виз|insurance|страхов|permit/i.test(t)) return 'reserve';
  return 'other';
}

function extractAdaptivePricesAndExpenses(text: string): PriceExtraction {
  const currency = detectCurrencyFromText(text);
  const hits = collectMoneyHits(text, currency);
  let divingPrice: number | null = null;
  let nonDivingPrice: number | null = null;
  const additionalExpenses: ParsedExpense[] = [];
  const lines = text
    .split(/\n+/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0)
    .slice(0, 1000);

  const byContext = (re: RegExp): MoneyHit | undefined =>
    hits.find((h) => {
      const ctx = text.slice(Math.max(0, h.idx - 90), Math.min(text.length, h.idx + 90));
      return re.test(ctx);
    });

  const diveHit = byContext(/дайвер|diver|diving|ныряющ/i);
  if (diveHit) divingPrice = diveHit.value;
  const nonDiveHit = byContext(/не\s*дайвер|non[-\s]?div|snorkel|accompanying/i);
  if (nonDiveHit) nonDivingPrice = nonDiveHit.value;

  if (divingPrice == null && hits.length > 0) {
    divingPrice = hits[0].value;
  }
  if (nonDivingPrice == null && hits.length > 1) {
    nonDivingPrice = hits[1].value;
  }

  let expIndex = 0;
  for (const line of lines) {
    if (!/доп|additional|extra|not included|excluded|перел[её]т|трансфер|meal|insurance|fee|сбор/i.test(line)) {
      continue;
    }
    const localHits = collectMoneyHits(line, currency);
    for (const h of localHits.slice(0, 2)) {
      expIndex += 1;
      additionalExpenses.push({
        id: `import-expense-${expIndex}`,
        expenseType: inferExpenseType(line),
        description: line.slice(0, 180),
        cost: h.value,
        currency: h.currency || currency,
      });
      if (additionalExpenses.length >= 12) break;
    }
    if (additionalExpenses.length >= 12) break;
  }

  return {
    currency,
    divingPrice,
    nonDivingPrice,
    additionalExpenses,
  };
}

function mergeImageCandidates(
  jsonLdUrls: string[] | undefined,
  baseUrl: string,
  scraped: string[],
  max: number,
): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of [...(jsonLdUrls || []), ...scraped]) {
    if (!raw?.trim()) continue;
    let abs = raw.trim();
    if (!/^https?:\/\//i.test(abs)) {
      const r = resolveUrl(baseUrl, abs);
      if (!r) continue;
      abs = r;
    }
    try {
      assertFetchableUrl(abs);
    } catch {
      continue;
    }
    if (isLikelyJunkImageUrl(abs)) continue;
    if (seen.has(abs)) continue;
    seen.add(abs);
    out.push(abs);
    if (out.length >= max) break;
  }
  return out;
}

const LISTING_PATH_BAD =
  /\/(contact|contacts|login|register|cart|policy|privacy|terms|search|tag|tags|category|author|blog)(\/|$)/i;

function heuristicPickTripDetailUrls(links: string[], maxPick: number): string[] {
  const scored = links
    .map((u) => {
      try {
        const p = new URL(u).pathname;
        const depth = p.split('/').filter(Boolean).length;
        let score = depth * 3;
        if (LISTING_PATH_BAD.test(p)) score -= 200;
        if (p.length > 35) score += 4;
        if (/\d{4}/.test(p)) score += 2;
        if (/tour|trip|poezdk|safari|liveaboard/i.test(p)) score += 5;
        return { u, score };
      } catch {
        return { u, score: -1 };
      }
    })
    .filter((x) => x.score >= 0)
    .sort((a, b) => b.score - a.score);
  const out: string[] = [];
  const seen = new Set<string>();
  for (const { u } of scored) {
    if (out.length >= maxPick) break;
    if (seen.has(u)) continue;
    seen.add(u);
    out.push(u);
  }
  return out;
}

@Injectable()
export class TripImportService {
  private readonly logger = new Logger(TripImportService.name);

  constructor(
    private readonly config: ConfigService,
    private readonly media: MediaService,
    private readonly tripsWrite: TripsWriteService,
  ) {}

  /** Если ключа нет — используется эвристический импорт без LLM. */
  private optionalOpenAiKey(): string | null {
    const k = this.config.get<string>('OPENAI_API_KEY')?.trim();
    return k || null;
  }

  private openAiModel(): string {
    return (
      this.config.get<string>('OPENAI_TRIP_IMPORT_MODEL')?.trim() ||
      'gpt-4o-mini'
    );
  }

  private async openAiChatJson(system: string, user: string): Promise<string> {
    const key = this.optionalOpenAiKey();
    if (!key) {
      throw new ServiceUnavailableException(
        'OPENAI_API_KEY не задан — эта операция требует модель',
      );
    }
    const res = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({
        model: this.openAiModel(),
        temperature: 0.15,
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: user },
        ],
        response_format: { type: 'json_object' },
      }),
    });
    const body = (await res.json()) as {
      error?: { message?: string };
      choices?: { message?: { content?: string } }[];
    };
    if (!res.ok) {
      const msg = body?.error?.message || res.statusText;
      throw new BadRequestException(`OpenAI: ${msg}`);
    }
    const text = body?.choices?.[0]?.message?.content?.trim();
    if (!text) {
      throw new BadRequestException('Пустой ответ модели');
    }
    return text;
  }

  async fetchPageHtml(url: string): Promise<{ html: string; finalUrl: string }> {
    assertFetchableUrl(url);
    try {
      const r = await axios.get<string>(url, {
        timeout: FETCH_TIMEOUT_MS,
        maxRedirects: 5,
        responseType: 'text',
        headers: {
          'User-Agent': USER_AGENT,
          Accept:
            'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        },
        maxContentLength: 8 * 1024 * 1024,
        validateStatus: (s) => s >= 200 && s < 400,
      });
      const finalUrl = r.request?.res?.responseUrl || url;
      return { html: r.data || '', finalUrl: String(finalUrl) };
    } catch (e) {
      const ax = e as AxiosError;
      this.logger.warn(`fetch ${url}: ${ax.message}`);
      throw new BadRequestException(
        `Не удалось загрузить страницу: ${ax.message}`,
      );
    }
  }

  extractVisibleTextAndImages(
    html: string,
    baseUrl: string,
    maxImages: number,
  ): { text: string; imageCandidates: string[] } {
    const $ = cheerio.load(html);
    $('script, style, noscript, svg').remove();

    const mainEl = $('main, article, [role="main"]').first();
    const focus = mainEl.length > 0 ? mainEl : $('body');
    $('header, nav, footer, aside, [role="navigation"], [role="banner"], [role="contentinfo"]').remove();
    const text = focus
      .text()
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 24_000);
    const $fb = cheerio.load(html);
    $fb('script, style, noscript, svg').remove();
    const fallbackBody = $fb('body').text().replace(/\s+/g, ' ').trim().slice(0, 24_000);
    const primaryText = text.length >= 120 ? text : fallbackBody;

    const $2 = cheerio.load(html);
    const ogImage = $2('meta[property="og:image"]').attr('content')?.trim();
    const ogAbs = ogImage ? resolveUrl(baseUrl, ogImage) : undefined;
    const seen = new Set<string>();
    const ordered: string[] = [];

    const push = (href: string | undefined) => {
      if (!href) return;
      const trimmed = href.trim();
      if (!trimmed || trimmed.startsWith('data:')) return;
      const abs = resolveUrl(baseUrl, trimmed);
      if (!abs) return;
      try {
        assertFetchableUrl(abs);
      } catch {
        return;
      }
      const low = abs.toLowerCase();
      if (low.endsWith('.svg') || low.includes('tracking')) return;
      if (isLikelyJunkImageUrl(abs)) return;
      if (!seen.has(abs)) {
        seen.add(abs);
        ordered.push(abs);
      }
    };
    const pushSrcset = (srcset: string | undefined) => {
      if (!srcset) return;
      for (const part of srcset.split(',')) {
        const urlPart = part.trim().split(/\s+/)[0];
        if (urlPart) push(urlPart);
      }
    };

    push(ogImage);
    $2('article img[src], main img[src], article img[data-src], main img[data-src]').each(
      (_, el) => {
        push($2(el).attr('src'));
        push($2(el).attr('data-src'));
        push($2(el).attr('data-original'));
        pushSrcset($2(el).attr('srcset'));
        pushSrcset($2(el).attr('data-srcset'));
      },
    );
    $2('picture source[srcset], source[srcset]').each((_, el) => {
      pushSrcset($2(el).attr('srcset'));
      pushSrcset($2(el).attr('data-srcset'));
    });
    $2('a[href]').each((_, el) => {
      const href = $2(el).attr('href');
      if (!href) return;
      if (/\.(jpe?g|png|webp)(\?|#|$)/i.test(href)) {
        push(href);
      }
    });
    $2('meta[property="og:image"], meta[name="twitter:image"], meta[property="og:image:secure_url"]').each(
      (_, el) => {
        push($2(el).attr('content'));
      },
    );
    $2('img[src], img[data-src], img[data-original]').each((_, el) => {
      push($2(el).attr('src'));
      push($2(el).attr('data-src'));
      push($2(el).attr('data-original'));
      pushSrcset($2(el).attr('srcset'));
      pushSrcset($2(el).attr('data-srcset'));
    });
    $2('link[rel="image_src"]').each((_, el) => {
      push($2(el).attr('href'));
    });

    const scored = ordered
      .map((u) => ({
        u,
        s: scoreImageUrl(u) + (ogAbs && u === ogAbs ? 40 : 0),
      }))
      .sort((a, b) => b.s - a.s);
    const imageCandidates = scored.slice(0, maxImages).map((x) => x.u);

    return { text: primaryText, imageCandidates };
  }

  collectSameOriginLinks(html: string, listingUrl: string, max: number): string[] {
    const base = assertFetchableUrl(listingUrl);
    const $ = cheerio.load(html);
    const out: string[] = [];
    const seen = new Set<string>();
    const anchors = $('a[href]').toArray();
    for (const el of anchors) {
      if (out.length >= max) break;
      const href = $(el).attr('href');
      if (!href) continue;
      const abs = resolveUrl(listingUrl, href);
      if (!abs) continue;
      try {
        assertFetchableUrl(abs);
      } catch {
        continue;
      }
      const u = new URL(abs);
      if (u.origin !== base.origin) continue;
      if (seen.has(abs)) continue;
      seen.add(abs);
      out.push(abs);
    }
    return out;
  }

  private async llmExtractTrip(
    pageUrl: string,
    text: string,
    imageCandidates: string[],
  ): Promise<LlmTripShape> {
    const system = `Ты извлекаешь данные о дайв-поездке/дайв-туре со страницы сайта.
Правила:
- country — обязательно на английском, полное название страны (например Egypt, Thailand), не "Unknown".
- description — только про тур: маршрут, даты, условия, цена; без меню сайта, футера, «копирайт».
- startDate и endDate — строго YYYY-MM-DD по тексту страницы; если диапазон в одном месяце — обе даты в этом месяце.
- tripType: "daily" только если тур от отеля/базы без переезда; иначе "safari" (в т.ч. лайвборд, переезд).
- imagePickIndexes — только индексы из переданного списка; исключай логотипы, иконки, аватары, баннеры «скидка».
Верни ТОЛЬКО JSON с полями:
title (string), description (string), country (string), region (string|null), tripType ("daily"|"safari"),
startDate, endDate (YYYY-MM-DD), totalSpots (number, по умолчанию 12),
minimumCertificationLevel (string|null), minimumDives (number|null),
nitroxAvailable (boolean), equipmentRentalAvailable (boolean),
divingPrice, nonDivingPrice (number|null), currency (ISO 4217, default USD),
imagePickIndexes (integer[], до 8).`;

    const textSlice = text.slice(0, 18_000);
    const user = `URL страницы: ${pageUrl}

Текст (основной контент, может быть обрезан):
${textSlice}

Кандидаты URL изображений (индекс = позиция в массиве):
${imageCandidates.map((u, i) => `${i}: ${u}`).join('\n')}`;

    const raw = await this.openAiChatJson(system, user);
    let parsed: LlmTripShape;
    try {
      parsed = JSON.parse(raw) as LlmTripShape;
    } catch {
      throw new BadRequestException('Модель вернула невалидный JSON');
    }
    return parsed;
  }

  /**
   * Импорт без OpenAI: JSON-LD, meta, чистый текст, эвристики дат/страны, скоринг картинок.
   */
  private heuristicExtractTrip(params: {
    pageUrl: string;
    html: string;
    text: string;
    imageCandidates: string[];
    jsonLdHints: JsonLdTripHints;
  }): { shape: LlmTripShape; extraWarnings: string[]; additionalExpenses: ParsedExpense[] } {
    const { pageUrl, html, text, imageCandidates, jsonLdHints: ld } = params;
    const extraWarnings: string[] = [];
    extraWarnings.push(
      'Импорт без OpenAI: данные собраны эвристикой — проверьте даты, страну, описание и фото.',
    );
    const $ = cheerio.load(html);
    const title =
      ld.title?.trim() ||
      $('meta[property="og:title"]').attr('content')?.trim() ||
      $('meta[name="twitter:title"]').attr('content')?.trim() ||
      $('title').first().text().trim() ||
      $('h1').first().text().trim() ||
      'Imported trip';

    let description =
      ld.description?.trim() ||
      $('meta[property="og:description"]').attr('content')?.trim() ||
      $('meta[name="description"]').attr('content')?.trim() ||
      '';
    if (!description.length) {
      const paras = $('article p, main p, .content p, .entry-content p')
        .map((_, el) => $(el).text().trim())
        .get()
        .filter((s) => s.length > 30);
      if (paras.length) {
        description = paras.slice(0, 5).join('\n\n').slice(0, 4000);
      }
    }
    if (!description.length && text.length > 0) {
      description = text.slice(0, 3200) + (text.length > 3200 ? '…' : '');
    }
    if (!description.length) {
      description = `Импорт с ${pageUrl}`;
    }

    const defaultYear = new Date().getFullYear();
    let start: string | undefined;
    let end: string | undefined;

    if (ld.start) start = ld.start;
    if (ld.end) end = ld.end;

    const metaPub = $('meta[property="article:published_time"]').attr('content');
    const metaMod = $('meta[property="article:modified_time"]').attr('content');
    const isoFromMeta = (s: string | undefined) =>
      typeof s === 'string' ? schemaIsoDate(s) : undefined;
    const pub = isoFromMeta(metaPub);
    const mod = isoFromMeta(metaMod);
    if (!start && pub) start = pub;
    if (!end && mod) end = mod;

    const corpus = `${title}\n${description}\n${text}\n${pageUrl}`;

    if (!start || !end) {
      const ru = parseRussianRangeInText(corpus, defaultYear);
      if (ru.start) start = start || ru.start;
      if (ru.end) end = end || ru.end;
    }

    if (!start || !end) {
      const slug = parseSlugDateRange(pageUrl, defaultYear);
      if (slug.start) start = start || slug.start;
      if (slug.end) end = end || slug.end;
    }

    if (!start || !end) {
      const dots = parseDotDatesInText(corpus);
      if (dots.start) start = start || dots.start;
      if (dots.end) end = end || dots.end;
    }

    if (!start || !end) {
      const isos = findIsoDatesInString(corpus);
      if (isos.length >= 2) {
        start = start || isos[0];
        end = end || isos[isos.length - 1];
      } else if (isos.length === 1) {
        start = start || isos[0];
        end = end || isos[0];
      }
    }

    if (!start || !end) {
      const one = parseSingleRussianDateInText(corpus, defaultYear);
      if (one) {
        start = start || one;
        end = end || one;
      }
    }

    if (start && !end) end = start;
    if (!start && end) start = end;

    if (!start || !end) {
      const d0 = new Date();
      d0.setDate(d0.getDate() + 14);
      const d1 = new Date();
      d1.setDate(d1.getDate() + 21);
      start = toIsoDate(d0.getFullYear(), d0.getMonth() + 1, d0.getDate());
      end = toIsoDate(d1.getFullYear(), d1.getMonth() + 1, d1.getDate());
      extraWarnings.push(
        'Даты на странице не распознаны — подставлены заглушки; укажите точные даты вручную.',
      );
    }

    if (start && end && start > end) {
      const t = start;
      start = end;
      end = t;
    }

    const locale = ($('html').attr('lang') || '').toLowerCase();
    let country =
      ld.country?.trim() ||
      inferCountryFromCorpus(corpus) ||
      undefined;
    if (!country) {
      if (locale.startsWith('ru')) country = 'Russia';
      else if (locale.startsWith('uk') || locale.startsWith('ua')) country = 'Ukraine';
      else if (locale.startsWith('de')) country = 'Germany';
      else country = 'Unknown';
      if (country === 'Unknown') {
        extraWarnings.push(
          'Страна не определена — выберите страну вручную (эвристика не нашла название в тексте).',
        );
      }
    }

    const tripType: 'daily' | 'safari' =
      /лайвборд|liveaboard|сафари|переезд|круиз|yacht|boat safari/i.test(
        `${title} ${description}`,
      )
        ? 'safari'
        : 'daily';

    const imagePickIndexes = pickImageIndexesByScore(imageCandidates, 8);
    const price = extractAdaptivePricesAndExpenses(corpus);

    const shape: LlmTripShape = {
      title,
      description,
      country,
      region: null,
      tripType,
      startDate: start,
      endDate: end,
      totalSpots: 12,
      minimumCertificationLevel: null,
      minimumDives: null,
      nitroxAvailable: false,
      equipmentRentalAvailable: false,
      divingPrice: price.divingPrice,
      nonDivingPrice: price.nonDivingPrice,
      currency: price.currency,
      imagePickIndexes,
    };
    return { shape, extraWarnings, additionalExpenses: price.additionalExpenses };
  }

  private async llmPickTripDetailUrls(
    listingUrl: string,
    links: string[],
    maxPick: number,
  ): Promise<string[]> {
    const system = `Ты помогаешь найти ссылки на ОТДЕЛЬНЫЕ страницы туров/поездок (детальные карточки), а не общие разделы.
Верни JSON: { "urls": string[] } — только абсолютные URL из переданного списка, не более ${maxPick} штук, без дубликатов.
Исключай: главную, контакты, блог-списки без конкретного тура, политику, корзину, логин.`;

    const user = `Страница каталога: ${listingUrl}

Ссылки (каждая строка — кандидат):
${links.join('\n')}`;

    const raw = await this.openAiChatJson(system, user);
    let parsed: { urls?: string[] };
    try {
      parsed = JSON.parse(raw) as { urls?: string[] };
    } catch {
      throw new BadRequestException('Модель вернула невалидный JSON (список URL)');
    }
    const urls = Array.isArray(parsed.urls) ? parsed.urls : [];
    const allowed = new Set(links);
    return urls.filter((u) => typeof u === 'string' && allowed.has(u));
  }

  private normalizeTrip(
    pageUrl: string,
    llm: LlmTripShape,
    imageCandidates: string[],
  ): {
    description: string;
    country: string;
    region: string | null;
    tripType: 'daily' | 'safari';
    startDate: string;
    endDate: string;
    totalSpots: number;
    minimumCertificationLevel: string | null;
    minimumDives: number | null;
    nitroxAvailable: boolean;
    equipmentRentalAvailable: boolean;
    priceDetails: Record<string, unknown>;
    pickedImageUrls: string[];
    additionalExpenses: ParsedExpense[];
  } {
    const title = (llm.title || 'Imported trip').trim();
    const desc = (llm.description || '').trim();
    const description =
      desc.length > 0 ? `${title}\n\n${desc}` : `${title}\n\n(Импорт с ${pageUrl})`;
    const country = (llm.country || 'Unknown').trim();
    if (!country) {
      throw new BadRequestException('Модель не определила country');
    }
    const tripType =
      llm.tripType === 'daily' ? 'daily' : llm.tripType === 'safari' ? 'safari' : 'safari';
    const start = (llm.startDate || '').trim();
    const end = (llm.endDate || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) {
      throw new BadRequestException(`Некорректная startDate: ${start}`);
    }
    const endOk = /^\d{4}-\d{2}-\d{2}$/.test(end) ? end : start;
    const totalSpots =
      typeof llm.totalSpots === 'number' && llm.totalSpots > 0
        ? Math.min(500, Math.floor(llm.totalSpots))
        : 12;
    const currency = (llm.currency || 'USD').trim() || 'USD';
    const adaptive = extractAdaptivePricesAndExpenses(`${title}\n${desc}`);
    const priceDetails: Record<string, unknown> = {
      currency,
      divingPrice: llm.divingPrice ?? adaptive.divingPrice ?? null,
      nonDivingPrice: llm.nonDivingPrice ?? adaptive.nonDivingPrice ?? null,
      roomPrices: [],
      yachtPrices: [],
    };

    const idxs = Array.isArray(llm.imagePickIndexes) ? llm.imagePickIndexes : [];
    const picked: string[] = [];
    for (const i of idxs) {
      if (typeof i !== 'number' || i < 0 || i >= imageCandidates.length) continue;
      const u = imageCandidates[i];
      if (u && !picked.includes(u)) picked.push(u);
      if (picked.length >= 12) break;
    }

    return {
      description,
      country,
      region: llm.region != null ? String(llm.region).trim() || null : null,
      tripType,
      startDate: start,
      endDate: endOk,
      totalSpots,
      minimumCertificationLevel: llm.minimumCertificationLevel ?? null,
      minimumDives:
        typeof llm.minimumDives === 'number' ? Math.max(0, llm.minimumDives) : null,
      nitroxAvailable: Boolean(llm.nitroxAvailable),
      equipmentRentalAvailable: Boolean(llm.equipmentRentalAvailable),
      priceDetails,
      pickedImageUrls: picked,
      additionalExpenses: adaptive.additionalExpenses,
    };
  }

  private async mirrorImageUrls(
    urls: string[],
    maxMirror: number,
  ): Promise<{ saved: string[]; errors: string[] }> {
    const saved: string[] = [];
    const errors: string[] = [];
    const slice = urls.slice(0, maxMirror);
    for (const u of slice) {
      try {
        assertFetchableUrl(u);
        const r = await axios.get<ArrayBuffer>(u, {
          timeout: FETCH_TIMEOUT_MS,
          responseType: 'arraybuffer',
          maxContentLength: 12 * 1024 * 1024,
          headers: { 'User-Agent': USER_AGENT },
          validateStatus: (s) => s === 200,
        });
        const buf = Buffer.from(r.data);
        const ext = extFromMime(r.headers['content-type'] as string, u);
        if (!['.jpg', '.png', '.webp'].includes(ext)) {
          errors.push(`${u}: неподдерживаемый тип`);
          continue;
        }
        const name = `import${ext}`;
        const { url: publicPath } = await this.media.save(buf, name);
        saved.push(publicPath);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        errors.push(`${u}: ${msg}`);
      }
    }
    return { saved, errors };
  }

  async importTripFromUrl(params: {
    url: string;
    diveCenterId: string;
    maxImageCandidates?: number;
    maxPhotosToMirror?: number;
  }): Promise<{
    tripId: string;
    warnings: string[];
    mirroredPhotoUrls: string[];
    externalPhotoUrlsKept: string[];
    sourceUrl: string;
  }> {
    await this.tripsWrite.assertDiveCenterExists(params.diveCenterId);
    const maxCand = Math.min(60, Math.max(4, params.maxImageCandidates ?? 24));
    const maxMirror = Math.min(12, Math.max(0, params.maxPhotosToMirror ?? 6));

    const { html, finalUrl } = await this.fetchPageHtml(params.url);
    const jsonLdHints = extractJsonLdTripHints(html);
    const { text, imageCandidates: scraped } = this.extractVisibleTextAndImages(
      html,
      finalUrl,
      maxCand,
    );
    const imageCandidates = mergeImageCandidates(
      jsonLdHints.imageUrls,
      finalUrl,
      scraped,
      maxCand,
    );
    const hasJsonLdSignal =
      Boolean(jsonLdHints.title?.trim()) ||
      Boolean(jsonLdHints.description && jsonLdHints.description.trim().length > 15) ||
      Boolean(jsonLdHints.start);
    if (text.length < 80 && !hasJsonLdSignal) {
      throw new BadRequestException(
        'Слишком мало текста на странице — возможно, нужен JS-рендер (пока не поддерживается)',
      );
    }

    const warnings: string[] = [];
    let heuristicExpenses: ParsedExpense[] = [];

    let llm: LlmTripShape;
    if (this.optionalOpenAiKey()) {
      llm = await this.llmExtractTrip(finalUrl, text, imageCandidates);
    } else {
      const h = this.heuristicExtractTrip({
        pageUrl: finalUrl,
        html,
        text,
        imageCandidates,
        jsonLdHints,
      });
      llm = h.shape;
      warnings.push(...h.extraWarnings);
      heuristicExpenses = h.additionalExpenses;
    }
    const norm = this.normalizeTrip(finalUrl, llm, imageCandidates);
    let mirrored: string[] = [];
    if (maxMirror > 0 && norm.pickedImageUrls.length > 0) {
      const m = await this.mirrorImageUrls(norm.pickedImageUrls, maxMirror);
      mirrored = m.saved;
      warnings.push(...m.errors);
    }

    const merged: string[] = [...mirrored];
    for (const u of norm.pickedImageUrls) {
      if (merged.length >= 8) break;
      if (!merged.includes(u)) merged.push(u);
    }
    const photoUrls = merged.length > 0 ? merged : [];

    const { id } = await this.tripsWrite.createTrip({
      organizerId: params.diveCenterId,
      organizerType: 'dive_center',
      tripType: norm.tripType,
      hotelId: null,
      yachtId: null,
      hotelLabel: null,
      yachtLabel: null,
      country: norm.country,
      region: norm.region,
      startDate: norm.startDate,
      endDate: norm.endDate,
      minimumCertificationLevel: norm.minimumCertificationLevel,
      minimumDives: norm.minimumDives,
      description: norm.description,
      photoUrls,
      totalSpots: norm.totalSpots,
      nitroxAvailable: norm.nitroxAvailable,
      equipmentRentalAvailable: norm.equipmentRentalAvailable,
      groupLeaderId: null,
      programDays: [],
      additionalExpenses:
        heuristicExpenses.length > 0
          ? heuristicExpenses
          : norm.additionalExpenses,
      priceDetails: norm.priceDetails,
      availableCourseIds: [],
    });

    const externalPhotoUrlsKept = photoUrls.filter(
      (p) => p.startsWith('http://') || p.startsWith('https://'),
    );

    return {
      tripId: id,
      warnings,
      mirroredPhotoUrls: mirrored,
      externalPhotoUrlsKept,
      sourceUrl: finalUrl,
    };
  }

  async importTripFromUrlForOwner(params: {
    url: string;
    diveCenterId: string;
    userId: string;
    userRole?: string;
  }) {
    await this.tripsWrite.assertUserCanImportTripsForDiveCenter(
      params.diveCenterId,
      params.userId,
      params.userRole,
    );
    return this.importTripFromUrl({
      url: params.url,
      diveCenterId: params.diveCenterId,
    });
  }

  async importTripsFromListing(params: {
    listingUrl: string;
    diveCenterId: string;
    maxTrips?: number;
    maxListingLinks?: number;
  }): Promise<{
    results: Awaited<ReturnType<TripImportService['importTripFromUrl']>>[];
    skipped: string[];
    pickedUrls: string[];
  }> {
    const maxTrips = Math.min(25, Math.max(1, params.maxTrips ?? 8));
    const maxLinks = Math.min(80, Math.max(10, params.maxListingLinks ?? 50));

    const { html, finalUrl } = await this.fetchPageHtml(params.listingUrl);
    const links = this.collectSameOriginLinks(html, finalUrl, maxLinks);
    if (links.length === 0) {
      throw new BadRequestException(
        'Не найдено ссылок на том же домене — проверьте URL каталога',
      );
    }

    const picked = this.optionalOpenAiKey()
      ? await this.llmPickTripDetailUrls(finalUrl, links, maxTrips)
      : heuristicPickTripDetailUrls(links, maxTrips);
    const limited = picked.slice(0, maxTrips);
    if (limited.length === 0) {
      throw new BadRequestException(
        this.optionalOpenAiKey()
          ? 'Модель не выбрала ни одной страницы тура — уточните URL или импортируйте по прямой ссылке'
          : 'Не удалось выбрать страницы туров по эвристике — задайте OPENAI_API_KEY или импортируйте по прямой ссылке на карточку тура',
      );
    }

    const results: Awaited<ReturnType<TripImportService['importTripFromUrl']>>[] =
      [];
    const skipped: string[] = [];
    for (const u of limited) {
      try {
        const r = await this.importTripFromUrl({
          url: u,
          diveCenterId: params.diveCenterId,
        });
        results.push(r);
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        skipped.push(`${u}: ${msg}`);
      }
      await new Promise((res) => setTimeout(res, 1200));
    }

    return { results, skipped, pickedUrls: limited };
  }
}
