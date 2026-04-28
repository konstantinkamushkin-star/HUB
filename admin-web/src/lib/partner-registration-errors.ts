/**
 * Человекочитаемые сообщения для формы заявки партнёра (Nest class-validator часто отдаёт англ. текст).
 */
export function formatPartnerRegistrationApiError(
  raw: string | null,
  status: number,
): string {
  if (status === 502 || status === 503) {
    return "Сервер временно недоступен. Попробуйте через несколько минут.";
  }
  if (!raw?.trim()) {
    if (status === 400) {
      return "Запрос отклонён: проверьте заполнение полей и попробуйте снова.";
    }
    return "Не удалось отправить заявку. Попробуйте позже.";
  }

  const text = raw.trim();
  const lower = text.toLowerCase();

  if (
    lower.includes("personal data processing consent") ||
    lower.includes("personaldataconsent") ||
    lower.includes("personal data consent")
  ) {
    return "Нужно согласие на обработку персональных данных: отметьте чекбокс в блоке «Согласие» в начале формы (сразу под выбором дайв-центра или магазина), затем снова нажмите «Отправить на верификацию».";
  }

  if (lower.includes("personaldataconsenttext")) {
    return "Не удалось передать текст согласия на сервер. Обновите страницу (лучше с полным сбросом кэша) и отправьте заявку ещё раз.";
  }

  if (
    lower.includes("must be an email") ||
    lower.includes("isemail") ||
    (lower.includes("email") && lower.includes("invalid"))
  ) {
    return "Укажите корректный адрес электронной почты для связи.";
  }

  if (lower.includes("contactphone") || lower.includes("phone")) {
    if (lower.includes("minlength") || lower.includes("shorter")) {
      return "Телефон слишком короткий: укажите номер не короче 5 символов.";
    }
  }

  if (lower.includes("latitude") || lower.includes("longitude")) {
    return "Укажите точку на карте в допустимых пределах (широта -90...90°, долгота -180...180°).";
  }

  const commaParts = text
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (commaParts.length >= 3) {
    return "Сервер не принял данные: проверьте все обязательные поля, выберите место на карте и отметьте согласие на обработку персональных данных в начале формы.";
  }

  if (text.length > 400) {
    return `${text.slice(0, 380)}...`;
  }

  return text;
}
