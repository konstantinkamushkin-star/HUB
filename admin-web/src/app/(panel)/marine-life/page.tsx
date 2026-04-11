import { JsonPanel } from "@/components/panel/JsonPanel";

export default function MarineLifePage() {
  return (
    <JsonPanel
      title="Морская жизнь"
      description="CRUD видов: GET/POST/PATCH/DELETE /admin/marine-species. Удаление — опасное действие (заголовок + reason). Право: manage:marine_life."
      apiPath="/admin/marine-species?limit=100"
    />
  );
}
