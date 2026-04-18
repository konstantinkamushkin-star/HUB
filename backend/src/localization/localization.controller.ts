import { Controller, Get, Param } from '@nestjs/common';

/**
 * iOS `LocalizationService` merges optional remote strings over bundled resources.
 * Empty JSON = no overrides; avoids 404 in logs when CDN/backend has no phrase pack.
 */
@Controller('localization')
export class LocalizationController {
  @Get(':language')
  get(@Param('language') _language: string): Record<string, Record<string, string>> {
    return {};
  }
}
