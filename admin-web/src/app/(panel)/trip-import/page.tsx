"use client";

import { useState } from "react";
import { apiRequest } from "@/lib/api";

type ImportOneResult = {
  tripId: string;
  warnings: string[];
  mirroredPhotoUrls: string[];
  externalPhotoUrlsKept: string[];
  sourceUrl: string;
};

type ImportListingResult = {
  results: ImportOneResult[];
  skipped: string[];
  pickedUrls: string[];
};

export default function TripImportPage() {
  const [diveCenterId, setDiveCenterId] = useState("");
  const [tripUrl, setTripUrl] = useState("");
  const [listingUrl, setListingUrl] = useState("");
  const [maxTrips, setMaxTrips] = useState(8);
  const [loadingOne, setLoadingOne] = useState(false);
  const [loadingList, setLoadingList] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [oneResult, setOneResult] = useState<ImportOneResult | null>(null);
  const [listResult, setListResult] = useState<ImportListingResult | null>(null);

  const runImportOne = async () => {
    setError(null);
    setOneResult(null);
    setLoadingOne(true);
    const res = await apiRequest<ImportOneResult>("/admin/trips/import/url", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        url: tripUrl.trim(),
        diveCenterId: diveCenterId.trim(),
        maxPhotosToMirror: 6,
      }),
    });
    setLoadingOne(false);
    if (!res.ok) {
      setError(res.errorMessage || "Ошибка импорта");
      return;
    }
    if (res.data) setOneResult(res.data);
  };

  const runImportListing = async () => {
    setError(null);
    setListResult(null);
    setLoadingList(true);
    const res = await apiRequest<ImportListingResult>("/admin/trips/import/listing", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        listingUrl: listingUrl.trim(),
        diveCenterId: diveCenterId.trim(),
        maxTrips,
      }),
    });
    setLoadingList(false);
    if (!res.ok) {
      setError(res.errorMessage || "Ошибка импорта списка");
      return;
    }
    if (res.data) setListResult(res.data);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-xl font-semibold text-white">Импорт поездок с сайта</h1>
        <p className="mt-2 text-sm text-zinc-400">
          Backend загружает HTML, извлекает текст и картинки, вызывает{" "}
          <strong className="text-zinc-300">OpenAI</strong> (нужен{" "}
          <code className="rounded bg-zinc-800 px-1">OPENAI_API_KEY</code> в{" "}
          <code className="rounded bg-zinc-800 px-1">backend/.env</code>), сохраняет поездку в{" "}
          <code className="rounded bg-zinc-800 px-1">trips</code> для выбранного дайв-центра и
          зеркалирует выбранные фото в <code className="rounded bg-zinc-800 px-1">/api/media/files/…</code>.
          Право: <code className="rounded bg-zinc-800 px-1">moderate:content</code>.
        </p>
        <p className="mt-2 text-xs text-amber-200/90">
          Убедитесь, что у вас есть право на парсинг целевого сайта. Страницы только с JS без HTML
          (SPA без SSR) могут не импортироваться.
        </p>
      </div>

      <label className="block text-sm text-zinc-300">
        UUID дайв-центра (organizer)
        <input
          value={diveCenterId}
          onChange={(e) => setDiveCenterId(e.target.value)}
          placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
          className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2 font-mono text-sm text-white outline-none focus:border-sky-600"
        />
      </label>

      {error ? (
        <div className="rounded-lg border border-red-900/50 bg-red-950/40 px-4 py-3 text-sm text-red-200">
          {error}
        </div>
      ) : null}

      <section className="space-y-3 rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
        <h2 className="text-lg font-medium text-white">Одна поездка — прямая ссылка</h2>
        <p className="text-sm text-zinc-500">
          URL страницы конкретного тура / сафари / поездки.
        </p>
        <input
          value={tripUrl}
          onChange={(e) => setTripUrl(e.target.value)}
          placeholder="https://example.com/tours/red-sea-april"
          className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none focus:border-sky-600"
        />
        <button
          type="button"
          disabled={loadingOne || !tripUrl.trim() || !diveCenterId.trim()}
          onClick={() => void runImportOne()}
          className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-500 disabled:opacity-50"
        >
          {loadingOne ? "Импорт…" : "Импортировать"}
        </button>
        {oneResult ? (
          <pre className="max-h-80 overflow-auto rounded-lg bg-zinc-950 p-3 text-xs text-zinc-300">
            {JSON.stringify(oneResult, null, 2)}
          </pre>
        ) : null}
      </section>

      <section className="space-y-3 rounded-xl border border-zinc-800 bg-zinc-900/40 p-4">
        <h2 className="text-lg font-medium text-white">Несколько поездок — страница каталога</h2>
        <p className="text-sm text-zinc-500">
          Укажите URL списка туров на том же домене. Модель выберет ссылки на карточки туров, затем
          каждая страница импортируется как отдельная поездка.
        </p>
        <input
          value={listingUrl}
          onChange={(e) => setListingUrl(e.target.value)}
          placeholder="https://example.com/tours"
          className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none focus:border-sky-600"
        />
        <label className="flex items-center gap-2 text-sm text-zinc-400">
          Макс. туров
          <input
            type="number"
            min={1}
            max={25}
            value={maxTrips}
            onChange={(e) => setMaxTrips(Number(e.target.value))}
            className="w-20 rounded border border-zinc-700 bg-zinc-950 px-2 py-1 text-white"
          />
        </label>
        <button
          type="button"
          disabled={loadingList || !listingUrl.trim() || !diveCenterId.trim()}
          onClick={() => void runImportListing()}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
        >
          {loadingList ? "Импорт списка…" : "Импортировать каталог"}
        </button>
        {listResult ? (
          <pre className="max-h-96 overflow-auto rounded-lg bg-zinc-950 p-3 text-xs text-zinc-300">
            {JSON.stringify(listResult, null, 2)}
          </pre>
        ) : null}
      </section>
    </div>
  );
}
