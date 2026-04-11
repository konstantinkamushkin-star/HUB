import { JsonPanel } from "@/components/panel/JsonPanel";

export default function SupportPage() {
  return (
    <JsonPanel
      title="Поддержка (тикеты)"
      description="GET/POST/PATCH /admin/support/tickets. Фильтры: status, priority, assignedAdminId. Право: manage:support."
      apiPath="/admin/support/tickets?limit=50"
    />
  );
}
