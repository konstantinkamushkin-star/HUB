import { Suspense } from "react";
import { SearchClient } from "./SearchClient";

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <p className="text-zinc-400">Загрузка поиска…</p>
      }
    >
      <SearchClient />
    </Suspense>
  );
}
