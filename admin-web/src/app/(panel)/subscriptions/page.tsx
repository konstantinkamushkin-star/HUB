import { JsonPanel } from "@/components/panel/JsonPanel";

export default function SubscriptionsPage() {
  return (
    <JsonPanel
      title="Тарифы и подписки (каталог)"
      description="Каталог планов: GET/POST/PATCH/DELETE /admin/billing/plans. Назначение пользователю: PATCH /admin/users/:id/subscription (заголовок подтверждения + reason, тело: subscriptionTier, subscriptionExpiresAt). Право: manage:billing."
      apiPath="/admin/billing/plans?limit=50"
    />
  );
}
