# Требования к бэкенду для модуля поездок

## Обзор

Необходимо реализовать полный API для модуля поездок (Trips Module) с поддержкой создания, редактирования, просмотра и бронирования поездок.

## Модели данных

### Trip (Поездка)

```typescript
{
  id: string;
  organizerId: string; // User ID или Dive Center ID
  organizerType: 'dive_center' | 'user';
  tripType: 'daily' | 'safari';
  hotelId?: string; // Для daily trips
  yachtId?: string; // Для safari trips
  country: string;
  region?: string; // Регион внутри страны (например, "Шарм-эль-Шейх" для Египта)
  startDate: Date;
  endDate: Date;
  minimumCertificationLevel: string;
  minimumDives: number;
  description: string;
  photos: string[]; // URLs
  totalSpots: number;
  bookedSpots: number;
  participants: TripParticipant[];
  availableCourses: string[]; // Course IDs
  nitroxAvailable: boolean;
  groupLeaderId?: string; // Instructor ID (только для dive centers)
  program: TripProgramDay[];
  additionalExpenses: AdditionalExpense[];
  equipmentRentalAvailable: boolean;
  priceDetails: PriceDetails;
  createdAt: Date;
  updatedAt: Date;
}

interface TripParticipant {
  id: string;
  userId: string;
  name: string;
  email?: string;
  phoneNumber?: string;
  certificationLevel?: string;
  isDiving: boolean;
  bookedAt: Date;
}

interface TripProgramDay {
  id: string;
  date: Date;
  activities: ProgramActivity[];
  description?: string;
}

interface ProgramActivity {
  id: string;
  time: string; // "09:00"
  activity: string;
  diveSiteId?: string;
  diveCenterId?: string;
  notes?: string;
}

interface AdditionalExpense {
  id: string;
  expenseType: 'flight' | 'transfer' | 'nutrition' | 'reserve' | 'other';
  description: string;
  cost: number;
  currency: string;
}

interface PriceDetails {
  roomPrices?: RoomPrice[]; // Для daily trips
  yachtPrices?: YachtPrice[]; // Для safari trips
  divingPrice?: number;
  nonDivingPrice?: number;
  currency: string;
}

interface RoomPrice {
  id: string;
  roomType: string; // "Single", "Double", "Triple"
  divingPrice: number;
  nonDivingPrice: number;
}

interface YachtPrice {
  id: string;
  cabinType: string; // "Standard", "Deluxe", "Master"
  divingPrice: number;
  nonDivingPrice: number;
}
```

### Hotel (Отель)

```typescript
{
  id: string;
  name: string;
  description: string;
  location: {
    address: string;
    city: string;
    country: string;
    latitude?: number;
    longitude?: number;
  };
  photos: string[];
  rating?: number;
  amenities: string[];
  roomTypes: RoomType[];
  createdAt: Date;
  updatedAt: Date;
}

interface RoomType {
  id: string;
  name: string; // "Single", "Double", "Triple"
  description: string;
  maxOccupancy: number;
}
```

### Yacht (Яхта)

```typescript
{
  id: string;
  name: string;
  description: string;
  photos: string[];
  length?: number; // в метрах
  capacity: number;
  cabinTypes: CabinType[];
  amenities: string[];
  createdAt: Date;
  updatedAt: Date;
}

interface CabinType {
  id: string;
  name: string; // "Standard", "Deluxe", "Master"
  description: string;
  capacity: number;
}
```

### Course (Курс)

```typescript
{
  id: string;
  name: string;
  level: 'basic' | 'advanced' | 'professional' | 'technical' | 'specialization';
  description: string;
  trainingSystems: string[]; // ["PADI", "SSI", "NAUI"]
  program: CourseModule[];
  duration: number; // в днях
  prerequisites?: string[]; // Required certifications
  diveCenterId?: string;
  instructorId?: string;
  createdAt: Date;
  updatedAt: Date;
}

interface CourseModule {
  id: string;
  title: string;
  description: string;
  duration: number; // в часах
  moduleType: 'theory' | 'confined_water' | 'open_water' | 'exam';
  order: number;
}
```

### Instructor (Инструктор) - Расширение существующей модели

Добавить поля:
```typescript
{
  photoURL?: string; // Главное фото
  description?: string; // Подробное описание
  trainingSystems: string[]; // ["PADI", "SSI", "NAUI"]
  credentials: InstructorCredential[];
}

interface InstructorCredential {
  id: string;
  title: string; // "PADI Master Instructor", "SSI Course Director"
  organization: string; // "PADI", "SSI"
  issueDate?: Date;
  credentialNumber?: string;
  description?: string;
}
```

## API Endpoints

### Trips

#### GET /api/trips
Получить список поездок с фильтрацией

Query параметры:
- `tripType`: 'daily' | 'safari'
- `country`: string
- `startDate`: ISO date string
- `endDate`: ISO date string
- `minCertificationLevel`: string
- `nitroxAvailable`: boolean
- `equipmentRentalAvailable`: boolean
- `availableSpots`: boolean (только поездки со свободными местами)

**Права доступа**: Все авторизованные пользователи

#### GET /api/trips/:id
Получить детали поездки

**Права доступа**: Все авторизованные пользователи

#### POST /api/trips
Создать поездку

**Права доступа**: 
- Dive Center Admin
- User с подпиской PRO (subscriptionStatus = 'active')

**Валидация**:
- Проверить права пользователя
- Для daily trips: hotelId обязателен
- Для safari trips: yachtId обязателен
- endDate > startDate
- totalSpots > 0

#### PUT /api/trips/:id
Обновить поездку

**Права доступа**: Только организатор поездки

#### DELETE /api/trips/:id
Удалить поездку

**Права доступа**: Только организатор поездки

#### POST /api/trips/:id/book
Забронировать поездку

Body:
```json
{
  "participants": [
    {
      "userId": "string",
      "name": "string",
      "email": "string",
      "phoneNumber": "string",
      "certificationLevel": "string",
      "isDiving": boolean
    }
  ]
}
```

**Права доступа**: Все авторизованные пользователи

**Валидация**:
- Проверить доступность мест
- Проверить соответствие сертификации требованиям
- Обновить bookedSpots

### Hotels

#### GET /api/hotels
Получить список отелей

**Права доступа**: Все авторизованные пользователи

#### GET /api/hotels/:id
Получить детали отеля

**Права доступа**: Все авторизованные пользователи

### Yachts

#### GET /api/yachts
Получить список яхт

**Права доступа**: Все авторизованные пользователи

#### GET /api/yachts/:id
Получить детали яхты

**Права доступа**: Все авторизованные пользователи

### Courses

#### GET /api/courses
Получить список курсов

Query параметры:
- `diveCenterId`: string (опционально)

**Права доступа**: Все авторизованные пользователи

#### GET /api/courses/:id
Получить детали курса

**Права доступа**: Все авторизованные пользователи

#### POST /api/courses
Создать курс

**Права доступа**: Dive Center Admin, Instructor

#### PUT /api/courses/:id
Обновить курс

**Права доступа**: Создатель курса или Dive Center Admin

#### DELETE /api/courses/:id
Удалить курс

**Права доступа**: Создатель курса или Dive Center Admin

### Instructors

#### GET /api/dive-centers/:id/instructors
Получить список инструкторов дайвцентра

**Права доступа**: Все авторизованные пользователи

## Тестовые данные

### Создание тестового дайвцентра

```bash
POST /api/auth/register
{
  "email": "testdive@example.com",
  "password": "12345678",
  "firstName": "Test",
  "lastName": "Dive Center",
  "role": "DIVE_CENTER_ADMIN"
}
```

После регистрации создать дайвцентр через админ панель или API.

### Создание 3 инструкторов

Для каждого инструктора:

```bash
POST /api/auth/register
{
  "email": "instructor1@example.com",
  "password": "12345678",
  "firstName": "John",
  "lastName": "Instructor",
  "role": "INSTRUCTOR",
  "diveCenterId": "<dive_center_id>"
}
```

Затем создать профиль инструктора:

```bash
POST /api/instructors
{
  "userId": "<user_id>",
  "name": "John Instructor",
  "photoURL": "https://example.com/photo.jpg",
  "description": "Experienced PADI instructor with 10 years of experience",
  "trainingSystems": ["PADI", "SSI"],
  "certifications": ["PADI Master Instructor", "SSI Course Director"],
  "languages": ["English", "Russian"],
  "bio": "Professional diving instructor",
  "credentials": [
    {
      "title": "PADI Master Instructor",
      "organization": "PADI",
      "issueDate": "2020-01-01",
      "credentialNumber": "PADI-12345"
    }
  ]
}
```

### Создание 10 курсов

Примеры курсов:

1. **Open Water Diver** (Basic)
   - Training Systems: ["PADI", "SSI"]
   - Duration: 3-4 days
   - Prerequisites: None

2. **Advanced Open Water Diver** (Advanced)
   - Training Systems: ["PADI", "SSI"]
   - Duration: 2-3 days
   - Prerequisites: ["Open Water Diver"]

3. **Rescue Diver** (Advanced)
   - Training Systems: ["PADI"]
   - Duration: 3-4 days
   - Prerequisites: ["Advanced Open Water Diver"]

4. **Divemaster** (Professional)
   - Training Systems: ["PADI"]
   - Duration: 2-4 weeks
   - Prerequisites: ["Rescue Diver"]

5. **Instructor Development Course** (Professional)
   - Training Systems: ["PADI"]
   - Duration: 1-2 weeks
   - Prerequisites: ["Divemaster"]

6. **Nitrox Diver** (Specialization)
   - Training Systems: ["PADI", "SSI"]
   - Duration: 1 day
   - Prerequisites: ["Open Water Diver"]

7. **Deep Diver** (Specialization)
   - Training Systems: ["PADI"]
   - Duration: 2 days
   - Prerequisites: ["Advanced Open Water Diver"]

8. **Wreck Diver** (Specialization)
   - Training Systems: ["PADI", "SSI"]
   - Duration: 2-3 days
   - Prerequisites: ["Advanced Open Water Diver"]

9. **Night Diver** (Specialization)
   - Training Systems: ["PADI"]
   - Duration: 1-2 days
   - Prerequisites: ["Open Water Diver"]

10. **Underwater Photography** (Specialization)
    - Training Systems: ["PADI"]
    - Duration: 2 days
    - Prerequisites: ["Open Water Diver"]

### Создание 5 поездок

#### Поездка 1: Daily Trip - Египет
```json
{
  "organizerType": "dive_center",
  "tripType": "daily",
  "hotelId": "<hotel_id>",
  "country": "Egypt",
  "startDate": "2026-03-01",
  "endDate": "2026-03-08",
  "minimumCertificationLevel": "Open Water",
  "minimumDives": 10,
  "description": "Week-long diving trip to Red Sea",
  "photos": [],
  "totalSpots": 12,
  "bookedSpots": 0,
  "availableCourses": ["<course_id_1>", "<course_id_2>"],
  "nitroxAvailable": true,
  "equipmentRentalAvailable": true,
  "priceDetails": {
    "roomPrices": [
      {
        "roomType": "Single",
        "divingPrice": 1200,
        "nonDivingPrice": 800
      },
      {
        "roomType": "Double",
        "divingPrice": 1000,
        "nonDivingPrice": 700
      }
    ],
    "currency": "USD"
  },
  "program": [
    {
      "date": "2026-03-01",
      "activities": [
        {
          "time": "09:00",
          "activity": "Check-in and equipment setup"
        },
        {
          "time": "14:00",
          "activity": "First dive - House Reef"
        }
      ]
    }
  ],
  "additionalExpenses": [
    {
      "expenseType": "flight",
      "description": "Round trip flight",
      "cost": 500,
      "currency": "USD"
    }
  ]
}
```

#### Поездка 2: Safari Trip - Мальдивы
```json
{
  "organizerType": "dive_center",
  "tripType": "safari",
  "yachtId": "<yacht_id>",
  "country": "Maldives",
  "startDate": "2026-04-15",
  "endDate": "2026-04-22",
  "minimumCertificationLevel": "Advanced Open Water",
  "minimumDives": 50,
  "description": "Liveaboard safari in Maldives",
  "totalSpots": 16,
  "nitroxAvailable": true,
  "equipmentRentalAvailable": false,
  "priceDetails": {
    "yachtPrices": [
      {
        "cabinType": "Standard",
        "divingPrice": 2500,
        "nonDivingPrice": 1800
      },
      {
        "cabinType": "Deluxe",
        "divingPrice": 3000,
        "nonDivingPrice": 2200
      }
    ],
    "currency": "USD"
  }
}
```

#### Поездка 3: Daily Trip - Таиланд
#### Поездка 4: Safari Trip - Индонезия
#### Поездка 5: Daily Trip - Филиппины

## Миграции базы данных

Необходимо создать следующие таблицы:

1. `trips` - основная таблица поездок
2. `trip_participants` - участники поездок
3. `trip_program_days` - дни программы
4. `trip_program_activities` - активности в программе
5. `trip_additional_expenses` - дополнительные расходы
6. `trip_price_details` - детали цен
7. `hotels` - отели
8. `hotel_room_types` - типы номеров
9. `yachts` - яхты
10. `yacht_cabin_types` - типы кают
11. `courses` - курсы
12. `course_modules` - модули курсов
13. `instructor_credentials` - регалии инструкторов

## Валидация и бизнес-логика

1. **Создание поездки**:
   - Проверить права пользователя (dive center или PRO подписка)
   - Для daily trips: проверить существование hotelId
   - Для safari trips: проверить существование yachtId
   - Проверить даты (endDate > startDate)
   - Проверить количество мест (totalSpots > 0)

2. **Бронирование**:
   - Проверить доступность мест
   - Проверить соответствие сертификации требованиям
   - Обновить bookedSpots
   - Создать записи участников

3. **Выбор лидера группы**:
   - Доступен только для dive centers
   - Инструктор должен принадлежать дайвцентру

## Примечания

- Все даты должны быть в формате ISO 8601
- Все цены должны иметь валюту
- Фотографии должны загружаться через отдельный endpoint для загрузки файлов
- Необходимо реализовать пагинацию для списков поездок
- Необходимо добавить индексы на часто используемые поля (country, startDate, endDate, tripType)
