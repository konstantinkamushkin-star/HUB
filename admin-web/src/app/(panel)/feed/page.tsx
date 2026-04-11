import { JsonPanel } from "@/components/panel/JsonPanel";

export default function FeedPage() {
  return (
    <JsonPanel
      title="Лента"
      description="Параметры: limit, offset, moderationStatus, userId, includeDeleted. Модерация: PATCH /admin/moderation/posts/:id/hide|restore."
      apiPath="/admin/registry/feed-posts?limit=50"
    />
  );
}
