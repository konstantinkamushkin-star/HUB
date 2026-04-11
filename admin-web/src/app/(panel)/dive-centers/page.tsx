import { JsonPanel } from "@/components/panel/JsonPanel";

export default function DiveCentersPage() {
  return (
    <JsonPanel
      title="Дайв-центры"
      description="Параметры: limit, offset, status, verificationStatus, query. Право: verify:entities. Новые заявки с формы — verificationStatus=pending, is_active=false до одобрения."
      apiPath="/admin/registry/dive-centers?limit=50"
    />
  );
}
