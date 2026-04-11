# Детальное ТЗ: Исправление поддержки поля `region` в бэкенде

## Проблема

Бэкенд возвращает ошибку 500: `"Internal server error"` при попытке обновить поездку с полем `region`. 

**Логи показывают:**
- Фронтенд отправляет: `"dtoRegion":"Марса-Алам"` ✅
- Бэкенд возвращает: `"statusCode":500` ❌
- В ответе бэкенда: `"regionInResponse":"nil"` ❌

## Что нужно проверить и исправить на бэкенде

### 1. Проверить, что поле `region` добавлено в DTO

**Файл:** `src/trips/dto/create-trip.dto.ts` (или аналогичный)

```typescript
export class CreateTripDto {
  // ... существующие поля
  country: string;
  region?: string;  // <-- ДОЛЖНО БЫТЬ ОПЦИОНАЛЬНЫМ
  startDate: Date;
  // ... остальные поля
}
```

**Файл:** `src/trips/dto/update-trip.dto.ts` (или аналогичный)

```typescript
export class UpdateTripDto {
  // ... существующие поля
  country: string;
  region?: string;  // <-- ДОЛЖНО БЫТЬ ОПЦИОНАЛЬНЫМ
  startDate: Date;
  // ... остальные поля
}
```

**КРИТИЧНО:** Поле `region` должно быть **опциональным** (`?` или `| undefined`), иначе бэкенд будет требовать его всегда.

### 2. Проверить валидацию

**Файл:** `src/trips/dto/create-trip.dto.ts` и `update-trip.dto.ts`

```typescript
import { IsOptional, IsString } from 'class-validator';

export class CreateTripDto {
  // ...
  @IsOptional()  // <-- КРИТИЧНО: должно быть опциональным
  @IsString()
  region?: string;
  // ...
}
```

**ВАЖНО:** 
- Убедитесь, что используется `@IsOptional()`, а не `@IsDefined()` или `@IsNotEmpty()`
- Убедитесь, что в валидации нет декораторов, которые запрещают дополнительные поля
- Проверьте, что в `ValidationPipe` не установлен `whitelist: true` без `forbidNonWhitelisted: false`

### 3. Проверить модель/схему базы данных

**Для Prisma (`prisma/schema.prisma`):**

```prisma
model Trip {
  // ... существующие поля
  country     String
  region      String?  // <-- ДОЛЖНО БЫТЬ ОПЦИОНАЛЬНЫМ (String?)
  startDate   DateTime
  // ... остальные поля
}
```

**После изменения схемы выполните:**
```bash
npx prisma migrate dev --name add_region_to_trip
npx prisma generate
```

**Для TypeORM (`src/trips/entities/trip.entity.ts`):**

```typescript
@Entity()
export class Trip {
  // ... существующие поля
  @Column()
  country: string;

  @Column({ nullable: true })  // <-- КРИТИЧНО: nullable: true
  region?: string;

  @Column()
  startDate: Date;
  // ... остальные поля
}
```

**После изменения выполните миграцию:**
```bash
npm run migration:generate -- -n AddRegionToTrip
npm run migration:run
```

### 4. Проверить маппинг в сервисе

**Файл:** `src/trips/trips.service.ts` (или аналогичный)

```typescript
async createTrip(createTripDto: CreateTripDto) {
  return this.tripRepository.create({
    // ... другие поля
    country: createTripDto.country,
    region: createTripDto.region,  // <-- ДОЛЖНО БЫТЬ ВКЛЮЧЕНО
    startDate: createTripDto.startDate,
    // ...
  });
}

async updateTrip(id: string, updateTripDto: UpdateTripDto) {
  return this.tripRepository.update(id, {
    // ... другие поля
    country: updateTripDto.country,
    region: updateTripDto.region,  // <-- ДОЛЖНО БЫТЬ ВКЛЮЧЕНО
    startDate: updateTripDto.startDate,
    // ...
  });
}
```

**КРИТИЧНО:** Убедитесь, что поле `region` **явно маппится** в методах `create` и `update`. Если используется spread оператор (`...updateTripDto`), убедитесь, что `region` не исключается.

### 5. Проверить, что `region` возвращается в ответах API

**Файл:** `src/trips/trips.service.ts` или контроллер

Убедитесь, что при возврате поездки поле `region` **включается в ответ**. Если используется сериализация/трансформация (например, `class-transformer`), проверьте:

```typescript
// Убедитесь, что region не исключается
@Expose()
region?: string;
```

Или проверьте, что в `@UseInterceptors(ClassSerializerInterceptor)` нет исключений для `region`.

### 6. Проверить логи бэкенда

**ВАЖНО:** Проверьте логи бэкенда при получении запроса с полем `region`. Ошибка 500 обычно означает:

1. **Ошибка валидации** - поле не проходит валидацию
2. **Ошибка базы данных** - поле не существует в схеме или миграция не применена
3. **Ошибка маппинга** - поле не маппится в сервисе
4. **Ошибка сериализации** - поле не может быть сериализовано

**Проверьте:**
- Логи валидации (если используется `ValidationPipe`)
- Логи базы данных (ошибки SQL)
- Логи сервиса (ошибки маппинга)
- Stack trace ошибки 500

### 7. Тестирование

**После исправления проверьте:**

1. **Создание поездки с region:**
```bash
POST /api/trips
{
  "country": "Египет",
  "region": "Марса-Алам",
  "tripType": "daily",
  "startDate": "2026-02-20T00:00:00.000Z",
  "endDate": "2026-02-27T00:00:00.000Z",
  // ... другие поля
}
```

**Ожидаемый результат:** Поездка создается с `region: "Марса-Алам"`

2. **Обновление поездки с region:**
```bash
PUT /api/trips/{id}
{
  "country": "Египет",
  "region": "Марса-Алам",
  // ... другие поля
}
```

**Ожидаемый результат:** Поездка обновляется с `region: "Марса-Алам"`

3. **Получение поездки:**
```bash
GET /api/trips/{id}
```

**Ожидаемый результат:** В ответе должно быть поле `region: "Марса-Алам"`

4. **Создание/обновление без region:**
```bash
POST /api/trips
{
  "country": "Египет",
  // region отсутствует
  // ... другие поля
}
```

**Ожидаемый результат:** Поездка создается с `region: null` или без поля `region`

## Чек-лист для проверки

- [ ] Поле `region?: string` добавлено в `CreateTripDto`
- [ ] Поле `region?: string` добавлено в `UpdateTripDto`
- [ ] Валидация использует `@IsOptional()` для `region`
- [ ] Поле `region String?` добавлено в модель/схему базы данных
- [ ] Миграция базы данных выполнена
- [ ] Поле `region` маппится в методах `createTrip` и `updateTrip`
- [ ] Поле `region` возвращается в ответах API
- [ ] Логи бэкенда проверены на ошибки
- [ ] Тесты пройдены (создание, обновление, получение с region и без)

## Дополнительная диагностика

Если проблема сохраняется после всех исправлений:

1. **Проверьте точный формат запроса:**
   - Откройте Network tab в браузере или используйте Postman
   - Проверьте, что поле `region` присутствует в JSON теле запроса
   - Проверьте Content-Type: `application/json`

2. **Проверьте middleware:**
   - Убедитесь, что middleware не фильтрует поле `region`
   - Проверьте порядок middleware (валидация должна быть после парсинга тела)

3. **Проверьте версию NestJS/Express:**
   - Убедитесь, что используется актуальная версия
   - Проверьте, что `ValidationPipe` настроен правильно

## Контакты для вопросов

Если нужна дополнительная информация, проверьте логи фронтенда:
- Фронтенд отправляет: `"dtoRegion":"Марса-Алам"`
- Бэкенд должен принять и сохранить это значение
- Бэкенд должен вернуть это значение в ответе
