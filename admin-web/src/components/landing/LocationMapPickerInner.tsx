"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AttributionControl,
  MapContainer,
  Marker,
  TileLayer,
  useMap,
  useMapEvents,
} from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

/** Как в iOS DiveCenterRegistrationView: MKCoordinateRegion center ~25, -40, span 45. */
const DEFAULT_CENTER: [number, number] = [25.0, -40.0];
const DEFAULT_ZOOM = 2;

function fixLeafletIcons() {
  delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: string })
    ._getIconUrl;
  L.Icon.Default.mergeOptions({
    iconRetinaUrl:
      "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
    iconUrl:
      "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
    shadowUrl:
      "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
  });
}

function MapViewSync({
  center,
  zoom,
}: {
  center: [number, number];
  zoom: number;
}) {
  const map = useMap();
  useEffect(() => {
    map.setView(center, zoom);
  }, [map, center, zoom]);
  return null;
}

/** Только клик по карте (как тап в MapKit), без перетаскивания метки. */
function MapClickHandler({
  onPick,
}: {
  onPick: (lat: number, lng: number) => void;
}) {
  useMapEvents({
    click(e) {
      onPick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export type LocationMapPickerInnerProps = {
  lat: number | null;
  lng: number | null;
  onChange: (lat: number, lng: number) => void;
  /** Как Swift: `[ad, ct, c].joined(", ")` для MKLocalSearch / Nominatim. */
  buildAddressLine: () => string;
  /** Проверка наличия страны, города и улицы (как guard в приложении). */
  hasFullAddress: () => boolean;
  appearance: "light" | "dark";
};

export function LocationMapPickerInner({
  lat,
  lng,
  onChange,
  buildAddressLine,
  hasFullAddress,
  appearance,
}: LocationMapPickerInnerProps) {
  const [mounted, setMounted] = useState(false);
  const [geocodeLoading, setGeocodeLoading] = useState(false);
  const [geocodeError, setGeocodeError] = useState<string | null>(null);

  useEffect(() => {
    fixLeafletIcons();
    setMounted(true);
  }, []);

  const position: [number, number] | null = useMemo(() => {
    if (lat == null || lng == null) return null;
    if (Number.isNaN(lat) || Number.isNaN(lng)) return null;
    return [lat, lng];
  }, [lat, lng]);

  const center: [number, number] = position ?? DEFAULT_CENTER;
  const zoom = position ? 14 : DEFAULT_ZOOM;

  const onPick = useCallback(
    (la: number, lo: number) => {
      setGeocodeError(null);
      onChange(la, lo);
    },
    [onChange],
  );

  async function geocodeFromAddressFields() {
    setGeocodeError(null);
    if (!hasFullAddress()) {
      setGeocodeError(
        "Для поиска укажите страну, город и адрес (улицу).",
      );
      return;
    }
    const query = buildAddressLine().trim();
    if (!query) {
      setGeocodeError(
        "Для поиска укажите страну, город и адрес (улицу).",
      );
      return;
    }
    setGeocodeLoading(true);
    try {
      const res = await fetch(
        `/api/geocode?q=${encodeURIComponent(query)}`,
      );
      if (!res.ok) {
        setGeocodeError("Не удалось найти адрес. Повторите попытку или укажите точку на карте.");
        return;
      }
      const raw: unknown = await res.json();
      const list = Array.isArray(raw) ? raw : [];
      if (list.length === 0) {
        setGeocodeError(
          "Адрес не найден. Уточните адрес или укажите точку на карте.",
        );
        return;
      }
      const first = list[0] as { lat?: string; lon?: string };
      const la = first.lat != null ? Number(first.lat) : NaN;
      const lo = first.lon != null ? Number(first.lon) : NaN;
      if (Number.isNaN(la) || Number.isNaN(lo)) {
        setGeocodeError("Некорректный ответ поиска");
        return;
      }
      onChange(la, lo);
    } catch {
      setGeocodeError(
        "Не удалось найти адрес. Повторите попытку или укажите точку на карте.",
      );
    } finally {
      setGeocodeLoading(false);
    }
  }

  const labelMuted =
    appearance === "light" ? "text-slate-600" : "text-zinc-400";
  const boxBorder =
    appearance === "light"
      ? "border-slate-200 bg-slate-50/80"
      : "border-zinc-600 bg-zinc-900/40";
  const btnPrimary =
    appearance === "light"
      ? "w-full rounded-xl border border-sky-200 bg-white py-2.5 text-sm font-medium text-sky-900 shadow-sm hover:bg-sky-50 disabled:opacity-50"
      : "w-full rounded-xl border border-zinc-500 bg-zinc-800 py-2.5 text-sm font-medium text-zinc-100 hover:bg-zinc-700 disabled:opacity-50";

  if (!mounted) {
    return (
      <div
        className={`flex h-[240px] items-center justify-center rounded-xl border ${boxBorder} text-sm ${labelMuted}`}
      >
        Загрузка карты…
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div>
        <p
          className={`text-sm font-medium ${appearance === "light" ? "text-slate-800" : "text-zinc-100"}`}
        >
          Точка на карте
        </p>
        <p className={`mt-1 text-xs ${labelMuted}`}>
          Нажмите на карту, чтобы поставить метку, или укажите страну, город и
          адрес выше и нажмите «Найти на карте по адресу».
        </p>
      </div>

      <div
        className={`partner-map-picker overflow-hidden rounded-xl border ${boxBorder} shadow-inner`}
      >
        <MapContainer
          center={center}
          zoom={zoom}
          className="z-0 h-[220px] w-full"
          scrollWheelZoom
          attributionControl={false}
        >
          <AttributionControl prefix={false} />
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapViewSync center={center} zoom={zoom} />
          <MapClickHandler onPick={onPick} />
          {position ? (
            <Marker position={position} />
          ) : null}
        </MapContainer>
      </div>

      <button
        type="button"
        className={btnPrimary}
        disabled={geocodeLoading}
        onClick={() => void geocodeFromAddressFields()}
      >
        {geocodeLoading ? "Поиск…" : "Найти на карте по адресу"}
      </button>

      {geocodeError ? (
        <p className="text-sm text-red-600" role="alert">
          {geocodeError}
        </p>
      ) : null}

      {position ? (
        <p className={`text-xs ${labelMuted}`}>Место выбрано.</p>
      ) : (
        <p className={`text-xs ${labelMuted}`}>
          Выберите точку на карте или сначала найдите адрес.
        </p>
      )}
    </div>
  );
}
