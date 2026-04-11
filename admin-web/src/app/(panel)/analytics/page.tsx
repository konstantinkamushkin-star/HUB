import { JsonPanel } from "@/components/panel/JsonPanel";

export default function AnalyticsPage() {
  return (
    <div className="space-y-10">
      <JsonPanel
        title="Расширенная сводка"
        description="Дашборд + метрики модулей ТЗ + разбивка пользователей по subscriptionTier. Право: view:admin_dashboard."
        apiPath="/admin/analytics/summary"
      />
      <JsonPanel
        title="Ошибки бекенда"
        description="Требуется view:error_stats."
        apiPath="/admin/error-stats"
      />
      <p className="text-sm text-zinc-500">
        Событийные воронки и DAU — при появлении конвейера событий; базовые
        агрегаты уже в сводке выше.
      </p>
    </div>
  );
}
