import { JsonPanel } from "@/components/panel/JsonPanel";

export default function RolesPage() {
  return (
    <JsonPanel
      title="Роли и права"
      description="Матрица ролей и разрешений. Требуется manage:roles."
      apiPath="/admin/roles"
    />
  );
}
