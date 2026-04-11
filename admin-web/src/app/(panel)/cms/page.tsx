import { JsonPanel } from "@/components/panel/JsonPanel";

export default function CmsPage() {
  return (
    <JsonPanel
      title="CMS (страницы)"
      description="Список: GET /admin/cms/pages. По slug: GET /admin/cms/pages/by-slug/:slug?locale=ru. Право: manage:cms."
      apiPath="/admin/cms/pages?limit=100"
    />
  );
}
