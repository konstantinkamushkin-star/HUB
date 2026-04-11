import { JsonPanel } from "@/components/panel/JsonPanel";

export default function CommentsPage() {
  return (
    <JsonPanel
      title="Комментарии"
      description="Параметры: limit, offset, moderationStatus, postId, userId, includeDeleted."
      apiPath="/admin/registry/comments?limit=50"
    />
  );
}
