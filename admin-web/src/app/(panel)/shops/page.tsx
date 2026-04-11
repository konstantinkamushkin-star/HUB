import { JsonPanel } from "@/components/panel/JsonPanel";

export default function ShopsPage() {
  return (
    <JsonPanel
      title="Магазины"
      description="Параметры: limit, offset, verificationStatus, query. Право: verify:entities. Новые заявки — verificationStatus=pending."
      apiPath="/admin/registry/shops?limit=50"
    />
  );
}
