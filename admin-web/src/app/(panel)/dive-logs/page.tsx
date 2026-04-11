import { JsonPanel } from "@/components/panel/JsonPanel";

export default function DiveLogsPage() {
  return (
    <JsonPanel
      title="Дайв-логи"
      description="Пагинация: limit, offset; фильтры: moderationStatus, userId. Право: moderate:content."
      apiPath="/admin/registry/dive-logs?limit=50"
    />
  );
}
