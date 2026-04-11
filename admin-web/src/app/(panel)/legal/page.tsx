import { JsonPanel } from "@/components/panel/JsonPanel";

export default function LegalPage() {
  return (
    <JsonPanel
      title="Право и compliance"
      description="Запросы на экспорт/удаление данных (GDPR и т.п.). Требуется manage:users."
      apiPath="/admin/compliance/requests"
    />
  );
}
