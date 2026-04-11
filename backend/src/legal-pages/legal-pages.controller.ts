import { Controller, Get, Header } from '@nestjs/common';
import { SkipThrottle } from '@nestjs/throttler';
import { LEGAL_HTML_AGREEMENT, LEGAL_HTML_PRIVACY } from './legal-html';

/**
 * Публичные HTML-страницы вне префикса /api (см. main.ts exclude).
 * На VPS настройте nginx: location = /privacy { proxy_pass http://127.0.0.1:PORT/privacy; } и то же для /agreement.
 */
@Controller()
export class LegalPagesController {
  @SkipThrottle()
  @Get('privacy')
  @Header('Content-Type', 'text/html; charset=utf-8')
  @Header('Cache-Control', 'public, max-age=3600')
  privacy(): string {
    return LEGAL_HTML_PRIVACY;
  }

  @SkipThrottle()
  @Get('agreement')
  @Header('Content-Type', 'text/html; charset=utf-8')
  @Header('Cache-Control', 'public, max-age=3600')
  agreement(): string {
    return LEGAL_HTML_AGREEMENT;
  }
}
