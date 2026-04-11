import { JsonPanel } from "@/components/panel/JsonPanel";

export default function AuditLogsPage() {
  return (
    <JsonPanel
      title="Аудит действий администраторов"
      description="Фильтры: adminId, action, targetType, targetId, limit."
      apiPath="/admin/audit-logs"
    />
  );
}
