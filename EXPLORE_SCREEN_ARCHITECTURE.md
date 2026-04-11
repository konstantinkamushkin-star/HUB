# Explore Screen Architecture

## Overview
A scalable, MVVM-based Explore screen for the diving mobile app with support for three categories: Dive Sites, Dive Centers, and Shops.

## Folder Structure

```
DiveHub/
├── Models/
│   ├── Shop.swift                          # Shop model with required fields
│   ├── ExploreCategory.swift               # Category enum and ExploreItem protocol
│   ├── DiveSite.swift                      # Updated with waterTemp field
│   └── DiveCenter.swift                    # Updated with certificationAgency, languages, nitroxAvailable, priceFrom
│
├── ViewModels/
│   └── GenericExploreViewModel.swift       # Generic ViewModel with category-specific state
│
├── Views/
│   └── Explore/
│       ├── ExploreView.swift               # Main Explore screen with segmented control
│       ├── ListCard.swift                  # Reusable list card component
│       └── ExploreMapView.swift            # Map view with clustering support
│
└── Services/
    ├── ExploreDataService.swift            # Mock data service with pagination
    ├── ExploreCacheService.swift           # Caching layer for offline support
    └── MockExploreData.swift               # Mock data for all three categories
```

## Key Features

### 1. Category Management
- **Three Categories**: Dive Sites, Dive Centers, Shops
- **Segmented Control**: Top-level category switcher
- **Independent State**: Each category maintains its own:
  - Filters
  - View mode (list/map)
  - Search query
  - Pagination state

### 2. Generic Architecture
- **ExploreItem Protocol**: Unified interface for all exploreable items
- **GenericExploreViewModel**: Single ViewModel handling all categories
- **Type-Safe Extensions**: Models conform to ExploreItem via extensions

### 3. Filtering System
- **Category-Specific Filters**:
  - `DiveSiteFilters`: siteType, difficulty, depth, rating, etc.
  - `DiveCenterFilters`: city, country, certificationAgency, languages, nitroxAvailable, priceFrom
  - `ShopFilters`: shopType, brands, serviceAvailable, rating
- **Active Filter Count**: Badge showing number of active filters
- **Quick Filter Chips**: Quick access to common filters

### 4. Search & Discovery
- **Dynamic Placeholders**: Search placeholder changes based on category
- **Real-time Search**: Search updates as user types
- **Category-Specific Search**: Searches relevant fields per category

### 5. View Modes
- **List View**: 
  - Reusable `ListCard` component
  - Pull-to-refresh support
  - Infinite scroll pagination
  - Category-specific details display
- **Map View**:
  - MapKit integration
  - Category-specific annotations
  - Color-coded markers
  - Tap to view details

### 6. Data Management
- **Pagination**: 20 items per page with infinite scroll
- **Caching**: 5-minute cache expiration
- **Offline Support**: Cached data available when offline
- **Async Loading**: All data loading is async/await

### 7. UI Components

#### ListCard
- Displays item name, rating, review count
- Category-specific subtitle and details
- Recommendation badge (based on user certification)
- Friends visited indicator
- Add to trip action button
- Detail chips (water temp, visibility, difficulty, etc.)

#### Quick Filter Chips
- Horizontal scrollable chips
- Active state indication
- Category-specific quick filters

#### Filter Button
- Badge showing active filter count
- Opens category-specific filter sheet

### 8. Bonus Features
- **Recommendation Badge**: Shows personalized recommendations
- **Friends Visited**: Indicator showing how many friends visited
- **Add to Trip**: Quick action to add items to trip

## Models

### Shop
```swift
struct Shop: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var type: ShopType // offline, online
    var brands: [String]
    var serviceAvailable: Bool
    var rating: Double
    var reviewCount: Int
    var location: Location
    // ... other fields
}
```

### Updated DiveSite
- Added `waterTemp: Double?` field

### Updated DiveCenter
- Added `certificationAgency: String?`
- Added `languages: [String]`
- Added `nitroxAvailable: Bool`
- Added `priceFrom: Double?`

## Usage

### Basic Usage
```swift
struct ContentView: View {
    var body: some View {
        ExploreView()
    }
}
```

### Customization
The Explore screen automatically handles:
- Category switching
- State persistence per category
- Data loading and caching
- Filter management
- Search functionality

## Future Enhancements

1. **Real Backend Integration**: Replace `ExploreDataService` with actual API calls
2. **Advanced Clustering**: Implement map clustering for better performance
3. **Personalization**: Implement recommendation algorithm based on user profile
4. **Social Features**: Integrate friends visited functionality
5. **Trip Integration**: Connect add to trip with trip management system

## Testing

The architecture supports:
- Mock data for development
- Easy testing with `MockExploreData`
- Isolated ViewModel for unit testing
- Reusable components for UI testing
