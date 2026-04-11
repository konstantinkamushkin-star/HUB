import { JsonPanel } from "@/components/panel/JsonPanel";

export default function ImportExportPage() {
  return (
    <JsonPanel
      title="Импорт и экспорт"
      description="Задачи импорта/экспорта данных. Требуется view:audit_logs для списка."
      apiPath="/admin/data-jobs"
    />
  );
}
