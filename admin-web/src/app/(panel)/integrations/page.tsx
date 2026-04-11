import { JsonPanel } from "@/components/panel/JsonPanel";

export default function IntegrationsPage() {
  return (
    <JsonPanel
      title="Интеграции"
      description="GET /admin/integrations. POST/PATCH с заголовком x-admin-confirm-dangerous-action и полем reason в теле. Секреты храните вне репозитория (vault). Право: manage:integrations."
      apiPath="/admin/integrations"
    />
  );
}
