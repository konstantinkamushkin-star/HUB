import { DiveSiteContributionsClient } from "./DiveSiteContributionsClient";

/** Dynamic route so production `next start` reliably serves this URL (static/Turbopack build was 404). */
export const dynamic = "force-dynamic";

export default function DiveSiteContributionsPage() {
  return <DiveSiteContributionsClient />;
}
