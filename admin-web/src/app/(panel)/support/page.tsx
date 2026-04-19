import { Suspense } from "react";
import { SupportTicketsClient } from "./SupportTicketsClient";

export const dynamic = "force-dynamic";

export default function SupportPage() {
  return (
    <Suspense
      fallback={
        <div
          style={{
            minHeight: "40vh",
            backgroundColor: "#09090b",
            color: "#a1a1aa",
            fontFamily: "system-ui, sans-serif",
            padding: 24,
          }}
        >
          Загрузка…
        </div>
      }
    >
      <SupportTicketsClient />
    </Suspense>
  );
}
