"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { getSiteOrigin } from "@/lib/site-origin";

const LocationMapPicker = dynamic(
  () =>
    import("./LocationMapPickerInner").then((m) => m.LocationMapPickerInner),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[240px] items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-500">
        Загрузка карты…
      </div>
    ),
  },
);

type Kind = "dive_center" | "shop";

type Props = {
  /** When false, hides the intro heading (e.g. on standalone /register-business). */
  showIntro?: boolean;
  /** Светлая тема для лендинга; тёмная — для встраивания на тёмный фон. */
  appearance?: "light" | "dark";
};

export function PartnerRegistrationForm({
  showIntro = true,
  appearance = "light",
}: Props) {
  const router = useRouter();
  const [kind, setKind] = useState<Kind>("dive_center");
  const [shopType, setShopType] = useState<"offline" | "online">("offline");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [country, setCountry] = useState("");
  const [city, setCity] = useState("");
  const [address, setAddress] = useState("");
  const [website, setWebsite] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [legalAccepted, setLegalAccepted] = useState(false);

  const c = useMemo(() => {
    if (appearance === "light") {
      return {
        wrap: "text-slate-800",
        h2: "text-slate-900",
        intro: "text-slate-600",
        toggleBar: "border-slate-200 bg-sky-50/90",
        inactive: "text-slate-600 hover:text-slate-900",
        radio: "text-slate-700",
        label: "text-slate-600",
        field:
          "mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100",
        error: "text-red-600",
        success: "text-emerald-700",
      };
    }
    return {
      wrap: "text-zinc-100",
      h2: "text-white",
      intro: "text-zinc-400",
      toggleBar: "border-zinc-800 bg-zinc-900/40",
      inactive: "text-zinc-400 hover:text-zinc-200",
      radio: "text-zinc-300",
      label: "text-zinc-400",
      field:
        "mt-1 w-full rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white outline-none focus:border-sky-600",
      error: "text-red-400",
      success: "text-emerald-400",
    };
  }, [appearance]);

  useEffect(() => {
    if (getToken()) router.replace("/dashboard");
  }, [router]);

  const needCoords =
    kind === "dive_center" || (kind === "shop" && shopType === "offline");

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);

    const lat = latitude.trim() ? Number(latitude.replace(",", ".")) : undefined;
    const lng = longitude.trim() ? Number(longitude.replace(",", ".")) : undefined;

    if (
      needCoords &&
      (lat === undefined ||
        lng === undefined ||
        Number.isNaN(lat) ||
        Number.isNaN(lng))
    ) {
      setError(
        "Выберите точку на карте или сначала найдите адрес.",
      );
      setLoading(false);
      return;
    }

    if (!legalAccepted) {
      setError(
        "Подтвердите ознакомление с Политикой конфиденциальности и Пользовательским соглашением.",
      );
      setLoading(false);
      return;
    }

    const origin =
      getSiteOrigin() ||
      (typeof window !== "undefined" ? window.location.origin : "");
    const privacyUrl = origin ? `${origin}/privacy` : "/privacy";
    const agreementUrl = origin ? `${origin}/agreement` : "/agreement";
    const personalDataConsentText =
      `Пользователь подтверждает ознакомление и принятие Политики конфиденциальности DiveHub (${privacyUrl}) ` +
      `и Пользовательского соглашения DiveHub (${agreementUrl}), а также даёт согласие на обработку персональных данных ` +
      `в соответствии с указанной Политикой. Оператор персональных данных: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274, ОГРНИП 310774632100411). ` +
      `Дата и время акцепта: ${new Date().toISOString()}.`;

    const body: Record<string, unknown> = {
      kind,
      name: name.trim(),
      description: description.trim() || undefined,
      contactEmail: contactEmail.trim(),
      contactPhone: contactPhone.trim(),
      country: country.trim(),
      city: city.trim(),
      address: address.trim() || undefined,
      website: website.trim() || undefined,
      personalDataConsent: true,
      personalDataConsentText,
    };

    if (kind === "shop") {
      body.shopType = shopType;
    }
    if (needCoords) {
      body.latitude = lat;
      body.longitude = lng;
    }

    const res = await apiRequest<{
      message?: string;
      verificationRequestId?: string;
    }>("/v1/partner-registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    setLoading(false);

    if (!res.ok) {
      setError(res.errorMessage ?? "Не удалось отправить заявку");
      return;
    }
    setSuccess(
      res.data?.message ??
        "Заявка отправлена. Супер-администратор проверит данные в разделе «Верификация».",
    );
  }

  return (
    <div className={c.wrap}>
      {showIntro ? (
        <>
          <h2 className={`text-xl font-semibold sm:text-2xl ${c.h2}`}>
            Регистрация в каталоге
          </h2>
          <p className={`mt-2 text-sm ${c.intro}`}>
            Заполните форму: будет создана черновая карточка (не отображается в
            приложении до активации). Обязательная проверка супер-админом —
            раздел «Верификация» в панели.
          </p>
        </>
      ) : null}

      <div className={`mt-6 flex rounded-lg border p-1 ${c.toggleBar}`}>
        <button
          type="button"
          onClick={() => setKind("dive_center")}
          className={`flex-1 rounded-md py-2 text-sm font-medium transition-colors ${
            kind === "dive_center"
              ? "bg-sky-500 text-white shadow-sm"
              : c.inactive
          }`}
        >
          Дайв-центр
        </button>
        <button
          type="button"
          onClick={() => setKind("shop")}
          className={`flex-1 rounded-md py-2 text-sm font-medium transition-colors ${
            kind === "shop" ? "bg-sky-500 text-white shadow-sm" : c.inactive
          }`}
        >
          Магазин
        </button>
      </div>

      {kind === "shop" ? (
        <div className={`mt-4 flex flex-wrap gap-4 text-sm ${c.radio}`}>
          <label className="flex cursor-pointer items-center gap-2">
            <input
              type="radio"
              name="shopType"
              checked={shopType === "offline"}
              onChange={() => setShopType("offline")}
            />
            Офлайн (нужны координаты)
          </label>
          <label className="flex cursor-pointer items-center gap-2">
            <input
              type="radio"
              name="shopType"
              checked={shopType === "online"}
              onChange={() => setShopType("online")}
            />
            Онлайн
          </label>
        </div>
      ) : null}

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label className={`block text-xs font-medium ${c.label}`}>
            Название
          </label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            minLength={2}
            className={c.field}
          />
        </div>
        <div>
          <label className={`block text-xs font-medium ${c.label}`}>
            Описание (необязательно)
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className={c.field}
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className={`block text-xs font-medium ${c.label}`}>
              Email для связи
            </label>
            <input
              type="email"
              value={contactEmail}
              onChange={(e) => setContactEmail(e.target.value)}
              required
              className={c.field}
            />
          </div>
          <div>
            <label className={`block text-xs font-medium ${c.label}`}>
              Телефон
            </label>
            <input
              type="tel"
              value={contactPhone}
              onChange={(e) => setContactPhone(e.target.value)}
              required
              minLength={5}
              className={c.field}
            />
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className={`block text-xs font-medium ${c.label}`}>
              Страна
            </label>
            <input
              value={country}
              onChange={(e) => setCountry(e.target.value)}
              required
              minLength={2}
              className={c.field}
            />
          </div>
          <div>
            <label className={`block text-xs font-medium ${c.label}`}>
              Город
            </label>
            <input
              value={city}
              onChange={(e) => setCity(e.target.value)}
              required
              minLength={2}
              className={c.field}
            />
          </div>
        </div>
        <div>
          <label className={`block text-xs font-medium ${c.label}`}>
            Улица и номер
          </label>
          <input
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            className={c.field}
          />
        </div>
        <div>
          <label className={`block text-xs font-medium ${c.label}`}>
            Сайт (необязательно)
          </label>
          <input
            type="url"
            value={website}
            onChange={(e) => setWebsite(e.target.value)}
            placeholder="https://"
            className={c.field}
          />
        </div>
        {needCoords ? (
          <LocationMapPicker
            lat={
              latitude.trim()
                ? (() => {
                    const n = Number(latitude.replace(",", "."));
                    return Number.isNaN(n) ? null : n;
                  })()
                : null
            }
            lng={
              longitude.trim()
                ? (() => {
                    const n = Number(longitude.replace(",", "."));
                    return Number.isNaN(n) ? null : n;
                  })()
                : null
            }
            onChange={(la, lo) => {
              setLatitude(la.toFixed(6));
              setLongitude(lo.toFixed(6));
            }}
            buildAddressLine={() =>
              [address, city, country]
                .map((s) => s.trim())
                .filter(Boolean)
                .join(", ")
            }
            hasFullAddress={() => {
              const c = country.trim();
              const ct = city.trim();
              const ad = address.trim();
              return Boolean(c.length > 0 && ct.length > 0 && ad.length > 0);
            }}
            appearance={appearance}
          />
        ) : null}

        {error ? (
          <p className={`text-sm ${c.error}`} role="alert">
            {error}
          </p>
        ) : null}
        {success ? (
          <p className={`text-sm ${c.success}`} role="status">
            {success}
          </p>
        ) : null}

        <div
          className={`rounded-xl border p-4 text-sm ${
            appearance === "light"
              ? "border-slate-200 bg-slate-50/80"
              : "border-zinc-700 bg-zinc-900/50"
          }`}
        >
          <label className={`flex cursor-pointer gap-3 ${c.radio}`}>
            <input
              type="checkbox"
              checked={legalAccepted}
              onChange={(e) => setLegalAccepted(e.target.checked)}
              className="mt-0.5 h-4 w-4 shrink-0 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
            />
            <span>
              Подтверждаю, что ознакомился(ась) с{" "}
              <Link
                href="/privacy"
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-sky-600 underline hover:text-sky-700"
              >
                Политикой конфиденциальности
              </Link>{" "}
              и{" "}
              <Link
                href="/agreement"
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-sky-600 underline hover:text-sky-700"
              >
                Пользовательским соглашением
              </Link>{" "}
              и принимаю их условия, включая обработку персональных данных в
              объёме, необходимом для рассмотрения заявки и работы каталога
              DiveHub.
            </span>
          </label>
        </div>

        <button
          type="submit"
          disabled={loading || !legalAccepted}
          className="w-full rounded-lg bg-gradient-to-r from-sky-500 to-cyan-500 py-2.5 text-sm font-semibold text-white shadow-md shadow-sky-500/25 transition hover:from-sky-400 hover:to-cyan-400 disabled:opacity-50"
        >
          {loading ? "Отправка…" : "Отправить на верификацию"}
        </button>
      </form>
    </div>
  );
}
