import { PlaceholderSection } from "@/components/panel/PlaceholderSection";

export default function MergePage() {
  return (
    <PlaceholderSection title="Слияние дубликатов">
      <p className="mb-3">
        Опасные операции выполняются через POST с заголовком{" "}
        <code className="rounded bg-zinc-800 px-1">
          x-admin-confirm-dangerous-action: true
        </code>{" "}
        и телом с полем <code className="rounded bg-zinc-800 px-1">reason</code>.
      </p>
      <ul className="list-inside list-disc space-y-1 text-zinc-300">
        <li>
          <code className="rounded bg-zinc-800 px-1">POST /api/admin/merge/users</code>
        </li>
        <li>
          <code className="rounded bg-zinc-800 px-1">
            POST /api/admin/merge/dive-centers
          </code>
        </li>
        <li>
          <code className="rounded bg-zinc-800 px-1">
            POST /api/admin/merge/dive-sites
          </code>
        </li>
      </ul>
    </PlaceholderSection>
  );
}
