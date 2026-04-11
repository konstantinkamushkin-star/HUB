import { JsonPanel } from "@/components/panel/JsonPanel";

export default function SettingsPage() {
  return (
    <JsonPanel
      title="Системные настройки"
      description="Ключ-значение настроек. Требуется manage:settings."
      apiPath="/admin/system-settings"
    />
  );
}
