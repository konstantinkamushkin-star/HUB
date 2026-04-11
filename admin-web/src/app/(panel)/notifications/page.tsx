import { JsonPanel } from "@/components/panel/JsonPanel";

export default function NotificationsPage() {
  return (
    <JsonPanel
      title="Уведомления"
      description="Кампании push/уведомлений. Требуется manage:settings."
      apiPath="/admin/notifications/campaigns"
    />
  );
}
