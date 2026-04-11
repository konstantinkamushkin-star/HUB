import { JsonPanel } from "@/components/panel/JsonPanel";

export default function FeatureFlagsPage() {
  return (
    <JsonPanel
      title="Feature flags"
      description="Флаги функциональности. Требуется manage:settings."
      apiPath="/admin/feature-flags"
    />
  );
}
