import { JsonPanel } from "@/components/panel/JsonPanel";

export default function ReportsPage() {
  return (
    <JsonPanel
      title="Жалобы и модерация"
      description="Очередь жалоб. Параметры: status, priority, targetType, limit."
      apiPath="/admin/reports"
    />
  );
}
