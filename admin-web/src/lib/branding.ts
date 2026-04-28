/** Меняйте при смене логотипа — это помогает сбросить кеш у клиентов. */
export const BRANDING_ASSET_VERSION = "1";

export const brandingLogoUrl = `/branding/logo.png?v=${BRANDING_ASSET_VERSION}`;
