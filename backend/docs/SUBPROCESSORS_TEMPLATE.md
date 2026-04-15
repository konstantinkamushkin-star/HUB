# Subprocessors Register (DiveHub)

Это рабочий реестр subprocessors и международных передач для текущего стека DiveHub.
Источник: фактические интеграции в коде и `.env.example`.

## 1) Общие правила

- Запрещено подключать новый сервис без записи в этот реестр.
- Для каждого subprocessor должен быть:
  - DPA/договор на обработку данных;
  - оценка категории данных;
  - описание трансграничной передачи и safeguards (если применимо).

## 2) Таблица subprocessors

| Provider | Service/Purpose | Data Categories | Region/Country | Transfer Mechanism | DPA/SCC Status | Owner | Last Review |
|---|---|---|---|---|---|---|---|
| VPS/Cloud host (см. `SERVER_HANDOFF_TEMPLATE.md`) | Хостинг API, БД/backup (если не fully self-hosted on-prem) | Account/profile data, auth data, logs, uploaded media | TBD (provider-specific) | Contract + security terms; SCC/adequacy if outside local jurisdiction | **TODO: заполнить провайдера и статус DPA** | DevOps Lead | 2026-04-14 |
| Stripe | Payment processing + webhooks (`/webhooks/stripe`) | Payment metadata, webhook payloads, transaction identifiers | TBD by Stripe account region | Stripe DPA + SCC (если применимо) | **TODO: подтвердить и приложить ссылку/номер DPA** | Finance Lead | 2026-04-14 |
| Google (Identity) | Verify Google ID token (`google-auth-library`) | OAuth identity payload: `sub`, `email`, `email_verified`, profile names | Global / TBD | Google Cloud/Identity contractual terms + SCC (если применимо) | **TODO: подтвердить юридический пакет** | Backend Lead | 2026-04-14 |
| Apple (Sign in with Apple) | Verify Apple identity token (`appleid.apple.com/auth/keys`) | OAuth identity payload: Apple `sub`, optional email | Global / TBD | Apple developer terms + contractual safeguards | **TODO: подтвердить юридический пакет** | Backend Lead | 2026-04-14 |
| Google Firebase Cloud Messaging (FCM) | Push notifications on Android | Device push token, notification metadata | Global / TBD | Google Firebase terms + SCC (если применимо) | **TODO: подтвердить DPA и retention** | Mobile Lead | 2026-04-14 |
| SMTP provider (from `SMTP_HOST`) | Transactional email (password reset, partner welcome) | Email address, reset code, message metadata | TBD (depends on chosen SMTP) | Contract + DPA + SCC/adequacy if cross-border | **TODO: заполнить конкретного SMTP-провайдера** | Backend Lead | 2026-04-14 |
| OpenAI (optional, only if enabled) | Trip import processing (`OPENAI_API_KEY`) | Trip source content (URL/extracted text), possible incidental personal data in source | Global / TBD | OpenAI terms + DPA (if enabled in prod) | **TODO: подтвердить usage in prod и legal basis** | Product/Backend Lead | 2026-04-14 |

## 2.1) Обнаруженные в коде интеграции (для трассировки)

- Stripe webhook stub: `backend/src/webhooks/stripe-webhook.controller.ts`.
- Google/Apple token verification: `backend/src/auth/oauth-id-token.util.ts`.
- SMTP integration: `backend/src/mail/mail.service.ts` + `.env.example` (`SMTP_*`).
- Android push (FCM): `DiveHubAndroid/app/build.gradle.kts` и `DiveHubAndroid/app/src/main/java/com/divehub/app/push/DiveHubFirebaseMessagingService.kt`.
- Optional OpenAI trip import: `.env.example` (`OPENAI_API_KEY`).

## 3) Оценка риска по каждому провайдеру

Для каждого нового поставщика заполнить:

- Какие PII передаются.
- Нужна ли передача специальных категорий данных (если да, отдельное обоснование).
- Минимизация данных: можно ли сократить объем.
- Retention policy у поставщика.
- Механизм удаления/экспорта данных.
- Контакты security/privacy у поставщика.

## 4) Контрольный список при добавлении провайдера

- [ ] Проведен security review.
- [ ] Подписан DPA (и SCC при необходимости).
- [ ] Обновлены privacy policy и внутренние документы.
- [ ] Добавлены условия retention/deletion.
- [ ] Проверено, что доступы ограничены least-privilege.
- [ ] Назначен владелец интеграции.

## 5) Ежеквартальный review

- [ ] Все записи актуальны.
- [ ] Нет “теневых” интеграций без owner'а.
- [ ] Удалены неиспользуемые поставщики и доступы.
- [ ] Privacy policy соответствует фактическому стэку.

