# Тестовые пользователи для DiveHub

Все пользователи используют один и тот же пароль: **`password123`**

## 👤 DIVER_BASIC (Базовый дайвер)

- **Email:** `basic@divehub.com`
- **Имя:** Jane Smith
- **Роль:** DIVER_BASIC
- **Описание:** Базовый аккаунт с ограниченными функциями

- **Email:** `alex@divehub.com`
- **Имя:** Alex Johnson
- **Роль:** DIVER_BASIC
- **Описание:** Базовый аккаунт с ограниченными функциями

## ⭐ DIVER_PRO (PRO дайвер)

- **Email:** `pro@divehub.com`
- **Имя:** John Diver
- **Роль:** DIVER_PRO
- **Описание:** PRO аккаунт с расширенными функциями

- **Email:** `maria@divehub.com`
- **Имя:** Maria Rodriguez
- **Роль:** DIVER_PRO
- **Описание:** PRO аккаунт с расширенными функциями

## 👨‍🏫 INSTRUCTOR (Инструктор)

- **Email:** `instructor@divehub.com`
- **Имя:** Mike Instructor
- **Роль:** INSTRUCTOR
- **Описание:** Инструктор, привязанный к дайв-центру

- **Email:** `instructor2@divehub.com`
- **Имя:** Anna Instructor
- **Роль:** INSTRUCTOR
- **Описание:** Инструктор, привязанный к дайв-центру

## 🏢 DIVE_CENTER_ADMIN (Администратор дайв-центра)

- **Email:** `center@divehub.com`
- **Имя:** Sarah Center
- **Роль:** DIVE_CENTER_ADMIN
- **Описание:** Администратор дайв-центра (Blue Ocean Dive Center)

- **Email:** `center2@divehub.com`
- **Имя:** Carlos Martinez
- **Роль:** DIVE_CENTER_ADMIN
- **Описание:** Администратор дайв-центра (Coral Reef Adventures)

## 👑 SUPER_ADMIN (Супер-администратор)

- **Email:** `admin@divehub.com`
- **Имя:** Admin User
- **Роль:** SUPER_ADMIN
- **Описание:** Супер-администратор с полным доступом

---

## Как использовать

1. Откройте приложение DiveHub
2. Нажмите "Login" (Войти)
3. Введите email и пароль `password123` для любого из пользователей выше
4. После входа вы увидите интерфейс, соответствующий роли пользователя:
   - **DIVER_BASIC/PRO** → Интерфейс дайвера (DiverTabView)
   - **INSTRUCTOR** → Интерфейс инструктора (InstructorTabView)
   - **DIVE_CENTER_ADMIN/SUPER_ADMIN** → Интерфейс администратора (AdminTabView)

## Примечания

- Все пользователи имеют подтвержденный email (`emailVerified: true`)
- Пароль для всех пользователей: `password123`
- Для пересоздания пользователей выполните: `cd divehub-backend && node prisma/seed.js`
