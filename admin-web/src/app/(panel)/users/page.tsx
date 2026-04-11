import { JsonPanel } from "@/components/panel/JsonPanel";

export default function UsersPage() {
  return (
    <JsonPanel
      title="Пользователи"
      description="Список и фильтры: query, status, role, limit. Требуется право manage:users."
      apiPath="/admin/users"
    />
  );
}
