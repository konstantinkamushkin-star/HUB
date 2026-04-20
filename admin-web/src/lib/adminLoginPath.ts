/**
 * Путь страницы входа в админ-панель (без публичных ссылок с лендинга).
 * При необходимости задайте NEXT_PUBLIC_ADMIN_LOGIN_PATH в окружении сборки.
 */
export const ADMIN_LOGIN_PATH =
  (typeof process !== "undefined" &&
    process.env.NEXT_PUBLIC_ADMIN_LOGIN_PATH?.trim()) ||
  "/staff/divehub-console";
