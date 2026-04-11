export type NavItem = {
  href: string;
  label: string;
  group: string;
};

/** Разделы панели в соответствии с ТЗ (десктоп, супер-админ). */
export const NAV_ITEMS: NavItem[] = [
  { group: "Обзор", href: "/dashboard", label: "Дашборд" },
  { group: "Обзор", href: "/analytics", label: "Аналитика и ошибки" },
  { group: "Пользователи и роли", href: "/users", label: "Пользователи" },
  { group: "Пользователи и роли", href: "/roles", label: "Роли и права" },
  { group: "Пользователи и роли", href: "/devices", label: "Устройства и сессии" },
  { group: "Дайвинг", href: "/dive-logs", label: "Дайв-логи" },
  { group: "Дайвинг", href: "/dive-centers", label: "Дайв-центры" },
  { group: "Дайвинг", href: "/shops", label: "Магазины" },
  { group: "Дайвинг", href: "/dive-sites", label: "Дайв-сайты" },
  { group: "Дайвинг", href: "/marine-life", label: "Морская жизнь" },
  { group: "Контент", href: "/feed", label: "Лента" },
  { group: "Контент", href: "/comments", label: "Комментарии" },
  { group: "Модерация", href: "/reports", label: "Жалобы и модерация" },
  { group: "Модерация", href: "/verification", label: "Верификация" },
  { group: "Коммуникации", href: "/notifications", label: "Уведомления" },
  { group: "Финансы", href: "/subscriptions", label: "Подписки и платежи" },
  { group: "Поддержка", href: "/support", label: "Поддержка" },
  { group: "Система", href: "/settings", label: "Системные настройки" },
  { group: "Система", href: "/feature-flags", label: "Feature flags" },
  { group: "Система", href: "/audit-logs", label: "Аудит" },
  { group: "Система", href: "/integrations", label: "Интеграции" },
  { group: "Система", href: "/cms", label: "CMS" },
  { group: "Данные", href: "/trip-import", label: "Импорт поездок (сайт)" },
  { group: "Данные", href: "/import-export", label: "Импорт / экспорт" },
  { group: "Данные", href: "/merge", label: "Слияние дубликатов" },
  { group: "Данные", href: "/legal", label: "Право и compliance" },
];
