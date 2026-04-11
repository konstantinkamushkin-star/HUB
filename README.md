# DiveHub - Diving Platform Application

A comprehensive iOS application for divers, dive centers, and instructors built with SwiftUI.

## Architecture Overview

The application follows a modular architecture with clear separation of concerns:

### Project Structure

```
DiveHub/
├── Models/              # Data models
│   ├── User.swift
│   ├── DiveSite.swift
│   ├── DiveCenter.swift
│   ├── Booking.swift
│   ├── DiveLog.swift
│   ├── Review.swift
│   ├── Chat.swift
│   └── Gear.swift
├── Services/            # Business logic and API services
│   ├── AuthenticationService.swift
│   ├── NetworkService.swift
│   ├── LocalizationService.swift
│   └── StorageService.swift
├── ViewModels/          # View models for MVVM pattern
│   ├── MapViewModel.swift
│   ├── ExploreViewModel.swift
│   ├── LogbookViewModel.swift
│   ├── SocialViewModel.swift
│   └── ReviewsViewModel.swift
├── Views/               # SwiftUI views
│   ├── MainTabView.swift
│   ├── Map/
│   ├── Explore/
│   ├── Logbook/
│   ├── Social/
│   ├── Profile/
│   ├── Auth/
│   ├── Detail/
│   └── Booking/
├── Utilities/           # Extensions and utilities
│   └── Extensions.swift
└── DiveHubApp.swift    # App entry point
```

## Features

### Core Features

1. **Multi-language Support**
   - English and Russian at launch
   - Dynamic language switching
   - Easy to add new languages

2. **User Roles**
   - Diver (Basic) - Free account with limited features
   - Diver (PRO) - Paid subscription with advanced features
   - Instructor - Linked to dive centers
   - Dive Center Admin - Full center management
   - Super Admin - Multi-center management

3. **Map & Directory**
   - Interactive map with dive sites and centers
   - Filtering by type, difficulty, depth, rating
   - Detailed place cards with reviews and AI summaries

4. **Dive Logbook**
   - Add dive entries with comprehensive data
   - Statistics and milestones
   - PRO features: photos, videos, sensor integration

5. **Booking System**
   - Multi-step booking wizard
   - Service selection, date/time, instructor, gear rental
   - Payment integration (online/on-site)

6. **Social Features (PRO)**
   - Friends system
   - Real-time friend tracking at resorts
   - Group trips and chats
   - Achievement system

7. **Review System**
   - Rate and review dive sites, centers, and instructors
   - AI-generated summaries
   - Sentiment analysis

## Technical Stack

- **Frontend**: SwiftUI (iOS native)
- **Architecture**: MVVM pattern
- **Networking**: URLSession with async/await
- **Storage**: UserDefaults + FileManager for offline support
- **Maps**: MapKit
- **Localization**: Custom LocalizationService

## Security

- HTTPS for all network requests
- Secure token storage (to be implemented with Keychain)
- Data encryption at rest (to be implemented)
- PCI-DSS compliant payment processing (to be integrated)

## Offline Support

- Cached dive sites and logs
- Offline logbook viewing
- Data sync when connection restored

## Future Enhancements

1. **Backend Integration**
   - Connect to REST API
   - WebSocket for real-time features
   - Push notifications

2. **Advanced Features**
   - Dive computer integration via Bluetooth
   - Photo/video upload and management
   - Advanced analytics and reporting
   - Chat system with WebSocket

3. **Admin Panel**
   - Web-based admin interface (React.js recommended)
   - Analytics dashboard
   - Content management

## Development Setup

1. Open `DiveHub.xcodeproj` in Xcode
2. Build and run on iOS Simulator or device
3. The app uses mock data for development

## Test Data

The app includes comprehensive test data for development and testing purposes, located in `DiveHub/Utilities/TestData.swift`. This file is only compiled in DEBUG mode.

### Available Test Data

1. **Instructor Bookings** (`TestData.instructorBookings`)
   - 7 bookings with various statuses (pending, confirmed, completed, cancelled)
   - Includes bookings for today, upcoming dates, and past dates
   - Different participants, gear rentals, and payment methods

2. **Admin Bookings** (`TestData.adminBookings`)
   - All instructor bookings plus additional admin-specific bookings
   - More comprehensive dataset for testing admin views

3. **Gear Items** (`TestData.gearItems`)
   - 11 gear items with different categories (wetsuit, BCD, regulator, fins, mask, etc.)
   - Various statuses: available, rented, maintenance, scrapped
   - Includes maintenance history and service records

4. **Instructors** (`TestData.instructors`)
   - 4 test instructors with different certification levels
   - PADI, SSI, and NAUI certifications
   - Linked to test dive center

### Using Test Data

Test data is automatically used in DEBUG mode when:
- The backend is unavailable (network errors)
- No user is logged in
- Environment variable `USE_TEST_DATA` is set to `"true"`

To force test data usage, set the environment variable in Xcode:
1. Edit Scheme → Run → Arguments
2. Add Environment Variable: `USE_TEST_DATA` = `true`

### Testing Screens

With test data, you can test all instructor and dive center admin screens:
- **Instructor Dashboard**: Shows today's and upcoming bookings
- **Instructor Schedule**: Calendar view with bookings
- **Client Bookings**: List of all bookings with filtering
- **Admin Dashboard**: Statistics and quick actions
- **Booking Management**: All bookings with status management
- **Gear Management**: All gear items with status filtering
- **Instructor Management**: List of instructors
- **Analytics**: Revenue and booking statistics
- **Calendar**: Calendar view of bookings

## API Integration

The app is structured to easily integrate with a backend API. Update the `baseURL` in `NetworkService.swift` and implement the actual API endpoints.

## Localization

To add a new language:
1. Add the language to `AppLanguage` enum
2. Add translations to `LocalizationService.loadTranslations()`
3. Or use proper Localizable.strings files

## License

Copyright © 2026 DiveHub. All rights reserved.
