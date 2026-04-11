import { Suspense } from "react";
import { DeviceSessionsClient } from "./DeviceSessionsClient";

export default function DevicesPage() {
  return (
    <Suspense fallback={<p className="text-zinc-400">Загрузка…</p>}>
      <DeviceSessionsClient />
    </Suspense>
  );
}
