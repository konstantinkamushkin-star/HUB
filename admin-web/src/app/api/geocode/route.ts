import { NextRequest, NextResponse } from "next/server";

/** Прокси к Nominatim (OSM): поиск по адресу для формы партнёра. */
export async function GET(req: NextRequest) {
  const q = req.nextUrl.searchParams.get("q")?.trim();
  if (!q) {
    return NextResponse.json({ error: "empty query" }, { status: 400 });
  }

  const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(q)}&limit=1`;
  const res = await fetch(url, {
    headers: {
      Accept: "application/json",
      "User-Agent": "DiveHubPartnerRegistration/1.0 (admin-web)",
    },
    cache: "no-store",
  });

  if (!res.ok) {
    return NextResponse.json(
      { error: "geocode upstream failed" },
      { status: 502 },
    );
  }

  const data: unknown = await res.json();
  return NextResponse.json(data);
}
