import { JsonPanel } from "@/components/panel/JsonPanel";

export default function DiveSitesPage() {
  return (
    <JsonPanel
      title="Дайв-сайты"
      description="Параметры: limit, offset, status, query. Право: verify:entities."
      apiPath="/admin/registry/dive-sites?limit=50"
    />
  );
}
