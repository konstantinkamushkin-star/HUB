# Исправление поддержки поля `region` в бэкенде

## Проблема

Бэкенд возвращает ошибку 500: `"Internal server error"` при попытке создать или обновить поездку с полем `region`. Это означает, что бэкенд не может обработать это поле и падает с внутренней ошибкой.

## Что нужно исправить на бэкенде

### 1. Добавить поле `region` в DTO для создания поездки

**Файл:** `src/trips/dto/create-trip.dto.ts` (или аналогичный)

```typescript
export class CreateTripDto {
  // ... существующие поля
  country: string;
  region?: string;  // <-- Добавить это поле (опциональное)
  startDate: Date;
  // ... остальные поля
}
```

### 2. Добавить поле `region` в DTO для обновления поездки

**Файл:** `src/trips/dto/update-trip.dto.ts` (или аналогичный)

```typescript
export class UpdateTripDto {
  // ... существующие поля
  country: string;
  region?: string;  // <-- Добавить это поле (опциональное)
  startDate: Date;
  // ... остальные поля
}
```

### 3. Обновить валидацию

Убедитесь, что валидация не запрещает поле `region`. Если используется `class-validator`:

```typescript
import { IsOptional, IsString } from 'class-validator';

export class CreateTripDto {
  // ...
  @IsOptional()
  @IsString()
  region?: string;
  // ...
}
```

**Важно:** Убедитесь, что в валидации нет декоратора, который запрещает дополнительные поля (например, `@IsDefined()` или строгая валидация, которая отклоняет неизвестные поля).

### 4. Добавить поле `region` в модель/схему Trip

**Для Prisma (`prisma/schema.prisma`):**

```prisma
model Trip {
  // ... существующие поля
  country     String
  region      String?  // <-- Добавить это поле (опциональное)
  startDate   DateTime
  // ... остальные поля
}
```

После изменения схемы выполните:
```bash
npx prisma migrate dev --name add_region_to_trip
```

**Для TypeORM (`src/trips/entities/trip.entity.ts`):**

```typescript
@Entity()
export class Trip {
  // ... существующие поля
  @Column()
  country: string;

  @Column({ nullable: true })
  region?: string;  // <-- Добавить это поле

  @Column()
  startDate: Date;
  // ... остальные поля
}
```

### 5. Обновить маппинг в сервисе

**Файл:** `src/trips/trips.service.ts` (или аналогичный)

```typescript
async createTrip(createTripDto: CreateTripDto) {
  return this.tripRepository.create({
    // ... другие поля
    country: createTripDto.country,
    region: createTripDto.region,  // <-- Добавить маппинг
    startDate: createTripDto.startDate,
    // ...
  });
}

async updateTrip(id: string, updateTripDto: UpdateTripDto) {
  return this.tripRepository.update(id, {
    // ... другие поля
    country: updateTripDto.country,
    region: updateTripDto.region,  // <-- Добавить маппинг
    startDate: updateTripDto.startDate,
    // ...
  });
}
```

### 6. Убедиться, что `region` возвращается в ответах API

Проверьте, что при возврате поездки поле `region` включается в ответ. Если используется сериализация/трансформация (например, `class-transformer`), убедитесь, что `region` не исключается.

### 7. Обновить Swagger/OpenAPI документацию (если используется)

Если используется Swagger, добавьте поле `region` в схему:

```typescript
@ApiProperty({ required: false, type: String })
region?: string;
```

## Проверка

После внесения изменений:

1. Перезапустите бэкенд сервер
2. Попробуйте создать поездку с полем `region` через API
3. Проверьте, что поездка сохраняется с регионом
4. Проверьте, что при получении поездки поле `region` возвращается в ответе

## Пример запроса

```json
POST /api/trips
{
  "country": "Египет",
  "region": "Шарм-эль-Шейх",
  "tripType": "daily",
  "startDate": "2026-02-20T00:00:00.000Z",
  "endDate": "2026-02-27T00:00:00.000Z",
  // ... другие поля
}
```

## Пример ответа

```json
{
  "id": "...",
  "country": "Египет",
  "region": "Шарм-эль-Шейх",
  "tripType": "daily",
  // ... другие поля
}
```
