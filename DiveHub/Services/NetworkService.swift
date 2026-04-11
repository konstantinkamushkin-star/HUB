//
//  NetworkService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Combine

struct EmptyResponse: Codable {}

enum FriendRequestError: LocalizedError {
    case alreadyExists
    
    var errorDescription: String? {
        switch self {
        case .alreadyExists:
            return "Friend request already sent or you are already friends with this user."
        }
    }
}

enum NetworkError: LocalizedError {
    case invalidURL
    case noData
    case decodingError
    case serverError(Int)
    /// NestJS / Nest-подобный JSON: `{ "message": "..." }` или `[String]` — показываем в UI.
    case serverErrorWithDetail(Int, String)
    /// Ответ UVM/FastAPI с телом JSON (`detail` / `error`) — удобно диагностировать 400 на устройстве.
    case visionModuleHTTPError(statusCode: Int, message: String)
    case networkUnavailable
    case unknown(Error)
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .noData:
            return "No data received"
        case .decodingError:
            return "Failed to decode response"
        case .serverError(let code):
            return "Server error: \(code)"
        case .serverErrorWithDetail(let code, let message):
            return "\(message) (HTTP \(code))"
        case .visionModuleHTTPError(let code, let message):
            return "Server error: \(code) — \(message)"
        case .networkUnavailable:
            return "Network unavailable"
        case .unknown(let error):
            return error.localizedDescription
        }
    }
}

/// Прогресс обработки видео на UVM (загрузка → сервер → скачивание); ETA — экстраполяция по текущей доле.
struct VideoUnderwaterProcessingProgress: Sendable {
    /// 0...1
    var fraction01: Double
    /// Оценка оставшегося времени, сек.
    var estimatedSecondsRemaining: TimeInterval
}

private enum UnderwaterVisionVideoTimeouts: Sendable {
    /// Пауза без входящих байт (сервер крутит видео).
    nonisolated static func requestSeconds() -> TimeInterval { 3600 }
    /// Весь цикл: upload + обработка + download.
    nonisolated static func resourceSeconds() -> TimeInterval { 10_800 }
    /// UVM/FastAPI часто не шлёт заголовки, пока весь MP4 не собран (Sea-Thru/ai по кадрам) — старый короткий watchdog давал ложный «таймаут».
    nonisolated static func headerStallWaitSeconds(sourceVideoDuration: TimeInterval) -> TimeInterval {
        let cap = resourceSeconds() - 60
        return min(cap, max(180, sourceVideoDuration * 120))
    }
    /// Редко: заголовок 200 есть, тело не началось (прокси/буфер).
    nonisolated static func bodyStallWaitSeconds(sourceVideoDuration: TimeInterval) -> TimeInterval {
        let cap = resourceSeconds() - 60
        return min(cap, max(300, sourceVideoDuration * 90))
    }
}

class NetworkService {
    static let shared = NetworkService()

    /// Production Nest API (TLS). WebSocket: `https` → `wss` в `chatWebSocketURL`.
    private static let productionAPIBaseURL = "https://api.dive-hub.ru"
    
    /// Пусто → встроенный default. Без завершающего `/`. Профиль → «Сервер (разработка)» в DEBUG.
    static let apiBaseURLUserDefaultsKey = "networkServiceAPIBaseURL"
    
    /// По умолчанию (DEBUG и Release) — прод `productionAPIBaseURL`. Свой URL: настройки / `UserDefaults` (`apiBaseURLUserDefaultsKey`), например `http://127.0.0.1:3000` для Nest только на симуляторе.
    var baseURL: String {
        let raw = UserDefaults.standard.string(forKey: Self.apiBaseURLUserDefaultsKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !raw.isEmpty {
            let saved = raw.hasSuffix("/") ? String(raw.dropLast()) : raw
            #if DEBUG
            #if !targetEnvironment(simulator)
            let lower = saved.lowercased()
            if lower.hasPrefix("http://127.0.0.1") || lower.hasPrefix("http://localhost") {
                // На iPhone loopback — это сам телефон; часто остаётся после симулятора / ошибки в настройках.
            } else {
                return saved
            }
            #else
            return saved
            #endif
            #else
            return saved
            #endif
        }
        return Self.productionAPIBaseURL
    }
    private let session: URLSession
    
    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 120
        configuration.timeoutIntervalForResource = 300
        configuration.httpShouldUsePipelining = false
        configuration.waitsForConnectivity = true
        self.session = URLSession(configuration: configuration)
    }

    // #region agent log
    private static func agentDebugLog(_ location: String, _ message: String, hypothesisId: String, data: [String: String] = [:]) {
        struct Payload: Encodable {
            let sessionId: String
            let location: String
            let message: String
            let hypothesisId: String
            let timestamp: Int64
            let data: [String: String]?
        }
        let payload = Payload(
            sessionId: "1c36fa",
            location: location,
            message: message,
            hypothesisId: hypothesisId,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            data: data.isEmpty ? nil : data
        )
        guard let body = try? JSONEncoder().encode(payload),
              let url = URL(string: "http://127.0.0.1:1024/ingest/244a844a-f2ae-44e5-b604-08078d1768c9") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("1c36fa", forHTTPHeaderField: "X-Debug-Session-Id")
        req.httpBody = body
        URLSession.shared.dataTask(with: req).resume()
    }
    // #endregion
    
    // MARK: - URL Helper
    
    /// Converts a relative image URL path to a full URL
    /// - Parameter path: The image path (can be relative like "/uploads/..." or already a full URL)
    /// - Returns: A full URL string, or nil if the path is empty
    func fullImageURL(from path: String?) -> String? {
        guard let path = path, !path.isEmpty else {
            return nil
        }
        
        // If it's already a full URL, return as is
        if path.hasPrefix("http://") || path.hasPrefix("https://") {
            return path
        }
        
        // If it's a relative path starting with "/", prepend baseURL
        if path.hasPrefix("/") {
            return baseURL + path
        }
        
        // Otherwise, assume it's a relative path and prepend baseURL + "/"
        return baseURL + "/" + path
    }
    
    // MARK: - HTTP error body (Nest / FastAPI)
    
    private static func extractAPIErrorMessage(from data: Data) -> String? {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        if let m = obj["message"] as? String, !m.isEmpty { return m }
        if let arr = obj["message"] as? [String], !arr.isEmpty {
            return arr.joined(separator: "; ")
        }
        if let detail = obj["detail"] as? String, !detail.isEmpty { return detail }
        return nil
    }
    
    private func httpFailureError(data: Data, statusCode: Int) -> NetworkError {
        if let msg = Self.extractAPIErrorMessage(from: data), !msg.isEmpty {
            return .serverErrorWithDetail(statusCode, msg)
        }
        return .serverError(statusCode)
    }
    
    /// Nest `ThrottlerGuard` (429). Exponential backoff so bursts from paged `getAllDiveSitesLegacy` recover instead of surfacing «Server error: 429».
    private func sessionDataWithRateLimitRetry(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let maxAttempts = 12
        var attempt = 0
        while true {
            attempt += 1
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw NetworkError.unknown(NSError(domain: "NetworkService", code: -1))
            }
            if http.statusCode == 429 && attempt < maxAttempts {
                let base = min(32.0, pow(2.0, Double(attempt - 1)))
                let jitter = Double.random(in: 0...0.35)
                try await Task.sleep(nanoseconds: UInt64((base + jitter) * 1_000_000_000))
                continue
            }
            return (data, http)
        }
    }
    
    // MARK: - JSON decoding (Nest + PostgreSQL timestamp shapes)
    
    static func apiJSONDecoder() -> JSONDecoder {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { decoder in
            try decodeJSONDate(from: decoder)
        }
        return d
    }
    
    private static func decodeJSONDate(from decoder: Decoder) throws -> Date {
        let c = try decoder.singleValueContainer()
        if let ms = try? c.decode(Double.self) {
            if ms > 1e15 { return Date(timeIntervalSince1970: ms / 1_000_000) }
            if ms > 1e12 { return Date(timeIntervalSince1970: ms / 1_000) }
            return Date(timeIntervalSince1970: ms)
        }
        let s = try c.decode(String.self).trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty {
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "Empty date string")
        }
        let isoFrac = ISO8601DateFormatter()
        isoFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = isoFrac.date(from: s) { return d }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        if let d = iso.date(from: s) { return d }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        for format in [
            "yyyy-MM-dd",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
        ] {
            df.dateFormat = format
            if let d = df.date(from: s) { return d }
        }
        throw DecodingError.dataCorruptedError(in: c, debugDescription: "Unparseable date: \(s)")
    }
    
    // MARK: - Generic Request Method
    
    func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod = .get,
        body: Encodable? = nil,
        headers: [String: String]? = nil
    ) async throws -> T {
        let fullURL = baseURL + endpoint
        
        guard let url = URL(string: fullURL) else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        #if DEBUG
        if fullURL.contains("127.0.0.1") || fullURL.contains("localhost") {
            request.setValue("close", forHTTPHeaderField: "Connection")
        }
        #endif
        
        // Add authentication token if available
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        // Add custom headers
        headers?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // Add body if provided
        if let body = body {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            request.httpBody = try encoder.encode(body)
            
            #if DEBUG
            if let jsonData = request.httpBody,
               let jsonString = String(data: jsonData, encoding: .utf8) {
                print("🌐 [NetworkService] \(method.rawValue) \(baseURL + endpoint)")
                print("📤 Request Body: \(jsonString)")}
            #endif
        } else {
            #if DEBUG
            print("🌐 [NetworkService] \(method.rawValue) \(baseURL + endpoint)")
            #endif
        }
        
        do {
            let (data, httpResponse) = try await sessionDataWithRateLimitRetry(for: request)
            
            #if DEBUG
            if let responseString = String(data: data, encoding: .utf8) {
                print("📥 Response [\(httpResponse.statusCode)]: \(responseString.prefix(500))")
            }
            #endif
            
            guard (200...299).contains(httpResponse.statusCode) else {
                // Log error response body for debugging
                if let responseString = String(data: data, encoding: .utf8) {
                    #if DEBUG
                    print("❌ [NetworkService] Error Response [\(httpResponse.statusCode)]: \(responseString)")
                    #endif
                }
                
                // Try to refresh token if 401 and we have a refresh token
                if httpResponse.statusCode == 401 && getAuthToken() != nil && KeychainService.shared.getRefreshToken() != nil {
                    do {
                        let newToken = try await refreshAccessToken()
                        // Retry the original request with new token
                        var retryRequest = URLRequest(url: url)
                        retryRequest.httpMethod = method.rawValue
                        retryRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
                        retryRequest.setValue("Bearer \(newToken)", forHTTPHeaderField: "Authorization")
                        
                        if let body = body {
                            let encoder = JSONEncoder()
                            encoder.dateEncodingStrategy = .iso8601
                            retryRequest.httpBody = try encoder.encode(body)
                        }
                        
                        let (retryData, retryHttpResponse) = try await sessionDataWithRateLimitRetry(for: retryRequest)
                        guard (200...299).contains(retryHttpResponse.statusCode) else {
                            throw httpFailureError(data: retryData, statusCode: retryHttpResponse.statusCode)
                        }
                        
                        let decoder = Self.apiJSONDecoder()
                        return try decoder.decode(T.self, from: retryData)
                    } catch {
                        // If refresh fails, clear tokens and throw error
                        clearAuthTokens()
                        throw NetworkError.serverError(401)
                    }
                }
                
                throw httpFailureError(data: data, statusCode: httpResponse.statusCode)
            }
            
            let decoder = Self.apiJSONDecoder()
            do {
                let decoded = try decoder.decode(T.self, from: data)
                return decoded
            } catch let decodingError as DecodingError {
                #if DEBUG
                print("❌ [NetworkService] Decoding Error: \(decodingError)")
                #endif
                throw NetworkError.decodingError
            }
        } catch let error as NetworkError {
            throw error
        } catch {
            #if DEBUG
            print("❌ [NetworkService] Unknown Error: \(error)")
            #endif
            throw NetworkError.unknown(error)
        }
    }
    
    // MARK: - Helper Methods
    
    func getAuthToken() -> String? {
        return KeychainService.shared.getAccessToken()
    }
    
    func saveAuthTokens(accessToken: String, refreshToken: String) {
        _ = KeychainService.shared.saveAccessToken(accessToken)
        _ = KeychainService.shared.saveRefreshToken(refreshToken)
    }
    
    func clearAuthTokens() {
        KeychainService.shared.clearAllTokens()
    }
    
    func refreshAccessToken() async throws -> String {guard let refreshToken = KeychainService.shared.getRefreshToken() else {throw NetworkError.serverError(401)
        }
        
        struct RefreshRequest: Codable {
            let refreshToken: String
        }
        
        struct RefreshResponse: Codable {
            let accessToken: String
            let refreshToken: String
        }
        
        let request = RefreshRequest(refreshToken: refreshToken)
        let response: RefreshResponse = try await self.request(
            endpoint: "/api/auth/refresh",
            method: .post,
            body: request
        )
        saveAuthTokens(accessToken: response.accessToken, refreshToken: response.refreshToken)
        return response.accessToken
    }
    
    enum HTTPMethod: String {
        case get = "GET"
        case post = "POST"
        case put = "PUT"
        case delete = "DELETE"
        case patch = "PATCH"
    }
}

// MARK: - API Endpoints

extension NetworkService {
    // Dive Sites
    func getDiveSites(filters: DiveSiteFilters? = nil, page: Int? = nil, limit: Int? = nil) async throws -> [DiveSite] {
        var endpoint = "/api/dive-sites"
        
        // Add language parameter for localization
        let language = LocalizationService.shared.currentLanguage.rawValue
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "language", value: language)]
        
        // Add pagination parameters if provided
        if let page = page {
            queryItems.append(URLQueryItem(name: "page", value: String(page)))
        }
        if let limit = limit {
            queryItems.append(URLQueryItem(name: "limit", value: String(limit)))
        }
        
        if let filters = filters {
            // Only add non-nil filters
            // Backend expects diveTypes as array parameter (multiple values)
            // For arrays in query strings, NestJS expects multiple query items with the same name
            if let siteType = filters.siteType {
                // Add multiple query items with the same name for array support
                // NestJS will parse this as an array
                queryItems.append(URLQueryItem(name: "diveTypes", value: siteType.rawValue))
            }
            if let difficulty = filters.difficulty {
                // Map DifficultyLevel enum to backend expected format (1-4)
                let difficultyValue: Int
                switch difficulty {
                case .beginner: difficultyValue = 1
                case .intermediate: difficultyValue = 2
                case .advanced: difficultyValue = 3
                case .expert: difficultyValue = 4
                }
                queryItems.append(URLQueryItem(name: "difficultyLevel", value: String(difficultyValue)))
            }
            // Backend expects minDepth (not depthMin)
            if let minDepth = filters.minDepth {
                queryItems.append(URLQueryItem(name: "minDepth", value: String(minDepth)))
            }
            // Backend expects maxDepth (not depthMax)
            if let maxDepth = filters.maxDepth {
                queryItems.append(URLQueryItem(name: "maxDepth", value: String(maxDepth)))
            }
            if let minRating = filters.minRating {
                queryItems.append(URLQueryItem(name: "minRating", value: String(minRating)))
            }
        }
            
        // Build query string manually with proper URL encoding
        // For arrays in NestJS with ParseArrayPipe, we can use comma-separated values
        // Format: diveTypes=wall,reef OR diveTypes=wall&diveTypes=reef
        // ParseArrayPipe with separator: ',' expects comma-separated values
        if !queryItems.isEmpty {
            var queryParts: [String] = []
            
            for item in queryItems {
                let name = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let value = (item.value ?? "").addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                
                // For diveTypes array, ParseArrayPipe with separator: ',' expects comma-separated values
                // But since we only have one value, we can send it directly
                // The pipe will parse it as an array with one element
                queryParts.append("\(name)=\(value)")
            }
            
            let queryString = queryParts.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
            
            #if DEBUG
            print("🔍 [NetworkService] getDiveSites with filters: endpoint=\(endpoint), filters=\(String(describing: filters)), page=\(String(describing: page)), limit=\(String(describing: limit))")
            #endif
        }
        
        let result: [DiveSite] = try await request(endpoint: endpoint)
        return result
    }

    /// Legacy `GET /api/dive-sites` is capped at 500 rows per request on the server; load the full list in pages.
    /// Spreads requests (Nest throttler) and stops if the server ignores `page`/`OFFSET` (duplicate windows).
    func getAllDiveSitesLegacy(filters: DiveSiteFilters? = nil) async throws -> [DiveSite] {
        let pageSize = 150
        var all: [DiveSite] = []
        var seen = Set<String>()
        var page = 1
        while true {
            let batch = try await getDiveSites(filters: filters, page: page, limit: pageSize)
            if batch.isEmpty { break }
            var appended = 0
            for site in batch where !seen.contains(site.id) {
                seen.insert(site.id)
                all.append(site)
                appended += 1
            }
            if appended == 0 { break }
            if batch.count < pageSize { break }
            page += 1
            if page > 200 { break }
            try await Task.sleep(nanoseconds: 450_000_000)
        }
        return all
    }
    
    func getDiveSite(id: String) async throws -> DiveSite {
        let language = LocalizationService.shared.currentLanguage.rawValue
        return try await request(endpoint: "/api/dive-sites/\(id)?language=\(language)")
    }
    
    // MARK: - Geo Search API (New optimized endpoints)
    
    /// Search dive sites by geolocation with radius (optimized endpoint)
    /// - Parameters:
    ///   - latitude: User's latitude
    ///   - longitude: User's longitude
    ///   - radius: Search radius in meters (default: 50000 = 50km)
    ///   - filters: Optional filters
    ///   - sortBy: Sort option: "distance", "rating", "popularity", "newest" (default: "distance")
    ///   - limit: Number of results (default: 20, max: 100)
    ///   - cursor: Cursor for pagination (from previous response)
    /// - Returns: Search result with sites and pagination info
    func searchDiveSitesByLocation(
        latitude: Double,
        longitude: Double,
        radius: Int = 50000,
        filters: DiveSiteFilters? = nil,
        sortBy: String = "distance",
        limit: Int = 20,
        cursor: String? = nil
    ) async throws -> DiveSiteSearchResult {
        var endpoint = "/api/v1/dive-sites/search"
        var queryItems: [URLQueryItem] = []
        
        // Required parameters
        queryItems.append(URLQueryItem(name: "lat", value: String(latitude)))
        queryItems.append(URLQueryItem(name: "lng", value: String(longitude)))
        queryItems.append(URLQueryItem(name: "radius", value: String(radius)))
        queryItems.append(URLQueryItem(name: "sort", value: sortBy))
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 100))))
        
        // Optional cursor
        if let cursor = cursor {
            queryItems.append(URLQueryItem(name: "cursor", value: cursor))
        }
        
        // Add filters
        if let filters = filters {
            if let difficulty = filters.difficulty {
                let difficultyValue: Int
                switch difficulty {
                case .beginner: difficultyValue = 1
                case .intermediate: difficultyValue = 2
                case .advanced: difficultyValue = 3
                case .expert: difficultyValue = 4
                }
                queryItems.append(URLQueryItem(name: "difficulty", value: String(difficultyValue)))
            }
            
            if let siteType = filters.siteType {
                // Backend expects site_types as an array. NestJS ValidationPipe with transform should parse single value as array.
                // If it doesn't work, we may need to pass the parameter multiple times or use ParseArrayPipe with separator.
                queryItems.append(URLQueryItem(name: "site_types", value: siteType.rawValue))
            }
            
            if let minDepth = filters.minDepth {
                queryItems.append(URLQueryItem(name: "min_depth", value: String(minDepth)))
            }
            
            if let maxDepth = filters.maxDepth {
                queryItems.append(URLQueryItem(name: "max_depth", value: String(maxDepth)))
            }
            
            if let minRating = filters.minRating {
                queryItems.append(URLQueryItem(name: "min_rating", value: String(minRating)))
            }
            
            if let country = filters.country {
                queryItems.append(URLQueryItem(name: "country", value: country))
            }
        }
        
        // Build query string
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }
        
        do {
            let result: DiveSiteSearchResult = try await request(endpoint: endpoint)
            return result
        } catch {
            throw error
        }
    }
    
    /// Search dive sites within a bounding box (for map view)
    /// - Parameters:
    ///   - north: Northern boundary latitude
    ///   - south: Southern boundary latitude
    ///   - east: Eastern boundary longitude
    ///   - west: Western boundary longitude
    ///   - filters: Optional filters
    ///   - limit: Maximum number of results (default: 500)
    /// - Returns: Array of dive sites
    func searchDiveSitesInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        filters: DiveSiteFilters? = nil,
        limit: Int = 500
    ) async throws -> [DiveSite] {
        var endpoint = "/api/v1/dive-sites/map"
        var queryItems: [URLQueryItem] = []
        
        queryItems.append(URLQueryItem(name: "north", value: String(north)))
        queryItems.append(URLQueryItem(name: "south", value: String(south)))
        queryItems.append(URLQueryItem(name: "east", value: String(east)))
        queryItems.append(URLQueryItem(name: "west", value: String(west)))
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 500))))
        
        // Add filters
        if let filters = filters {
            if let difficulty = filters.difficulty {
                let difficultyValue: Int
                switch difficulty {
                case .beginner: difficultyValue = 1
                case .intermediate: difficultyValue = 2
                case .advanced: difficultyValue = 3
                case .expert: difficultyValue = 4
                }
                queryItems.append(URLQueryItem(name: "difficulty", value: String(difficultyValue)))
            }
            
            if let siteType = filters.siteType {
                // Backend expects site_types as an array. NestJS ValidationPipe with transform should parse single value as array.
                // If it doesn't work, we may need to pass the parameter multiple times or use ParseArrayPipe with separator.
                queryItems.append(URLQueryItem(name: "site_types", value: siteType.rawValue))
            }
            
            if let minRating = filters.minRating {
                queryItems.append(URLQueryItem(name: "min_rating", value: String(minRating)))
            }
        }
        
        // Build query string
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }
        
        struct MapSearchResponse: Codable {
            let success: Bool
            let data: [DiveSite]
        }
        
        let response: MapSearchResponse = try await request(endpoint: endpoint)
        return response.data
    }
    
    /// Get popular dive sites (fallback when location is unavailable)
    /// - Parameters:
    ///   - country: Optional country filter
    ///   - limit: Number of results (default: 20, max: 100)
    /// - Returns: Array of popular dive sites
    func getPopularDiveSites(country: String? = nil, limit: Int = 20) async throws -> [DiveSite] {
        var endpoint = "/api/v1/dive-sites/popular"
        var queryItems: [URLQueryItem] = []
        
        if let country = country {
            queryItems.append(URLQueryItem(name: "country", value: country))
        }
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 100))))
        
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }
        
        struct PopularResponse: Codable {
            let success: Bool
            let data: [DiveSite]
        }
        
        let response: PopularResponse = try await request(endpoint: endpoint)
        return response.data
    }
    
    // MARK: - Response Models for Geo Search
    
    struct DiveSiteSearchResult: Codable {
        let success: Bool
        let data: [DiveSite]
        let pagination: PaginationInfo?
        let meta: SearchMeta?
    }
    
    struct PaginationInfo: Codable {
        let hasMore: Bool
        let nextCursor: String?
        let limit: Int
        
        enum CodingKeys: String, CodingKey {
            case hasMore = "has_more"
            case nextCursor = "next_cursor"
            case limit
        }
    }
    
    struct SearchMeta: Codable {
        let totalInRadius: Int?
        let queryTimeMs: Int?
        
        enum CodingKeys: String, CodingKey {
            case totalInRadius = "total_in_radius"
            case queryTimeMs = "query_time_ms"
        }
    }
    
    // Dive Centers
    func getDiveCenters(filters: DiveCenterFilters? = nil, page: Int? = nil, limit: Int? = nil) async throws -> [DiveCenter] {
        // Legacy /api/dive-centers endpoint is not present in backend.
        // Use v1 popular endpoint and apply optional filters client-side.
        let requestedLimit = max(1, min(limit ?? 100, 100))
        let countryFilter = filters?.country?.trimmingCharacters(in: .whitespacesAndNewlines)
        var centers = try await getPopularDiveCenters(
            country: (countryFilter?.isEmpty == false ? countryFilter : nil),
            limit: requestedLimit
        )
        
        if let city = filters?.city?.trimmingCharacters(in: .whitespacesAndNewlines), !city.isEmpty {
            centers = centers.filter { $0.location.city.localizedCaseInsensitiveContains(city) }
        }
        
        if let minRating = filters?.minRating {
            centers = centers.filter { $0.averageRating >= minRating }
        }
        
        if let page = page, page > 0 {
            let start = (page - 1) * requestedLimit
            guard start < centers.count else { return [] }
            let end = min(start + requestedLimit, centers.count)
            return Array(centers[start..<end])
        }
        
        return centers
    }
    
    func getDiveCenter(id: String) async throws -> DiveCenter {
        return try await request(endpoint: "/api/dive-centers/\(id)")
    }
    
    // MARK: - Geo Search API for Dive Centers (New optimized endpoints)
    
    /// Search dive centers by geolocation with radius (optimized endpoint)
    func searchDiveCentersByLocation(
        latitude: Double,
        longitude: Double,
        radius: Int = 50000,
        filters: DiveCenterFilters? = nil,
        sortBy: String = "distance",
        limit: Int = 20,
        cursor: String? = nil
    ) async throws -> DiveCenterSearchResult {
        var endpoint = "/api/v1/dive-centers/search"
        var queryItems: [URLQueryItem] = []
        
        // Required parameters
        queryItems.append(URLQueryItem(name: "lat", value: String(latitude)))
        queryItems.append(URLQueryItem(name: "lng", value: String(longitude)))
        queryItems.append(URLQueryItem(name: "radius", value: String(radius)))
        queryItems.append(URLQueryItem(name: "sort", value: sortBy))
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 100))))
        
        // Optional cursor
        if let cursor = cursor {
            queryItems.append(URLQueryItem(name: "cursor", value: cursor))
        }
        
        // Add filters
        if let filters = filters {
            if let minRating = filters.minRating {
                queryItems.append(URLQueryItem(name: "min_rating", value: String(minRating)))
            }
            
            if let country = filters.country {
                queryItems.append(URLQueryItem(name: "country", value: country))
            }
        }
        
        // Build query string
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }

        let result = try await request(endpoint: endpoint) as DiveCenterSearchResult

        return result
    }
    
    /// Search dive centers within a bounding box (for map view)
    func searchDiveCentersInBounds(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        filters: DiveCenterFilters? = nil,
        limit: Int = 500
    ) async throws -> [DiveCenter] {
        var endpoint = "/api/v1/dive-centers/map"
        var queryItems: [URLQueryItem] = []
        
        queryItems.append(URLQueryItem(name: "north", value: String(north)))
        queryItems.append(URLQueryItem(name: "south", value: String(south)))
        queryItems.append(URLQueryItem(name: "east", value: String(east)))
        queryItems.append(URLQueryItem(name: "west", value: String(west)))
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 500))))
        
        // Add filters
        if let filters = filters {
            if let minRating = filters.minRating {
                queryItems.append(URLQueryItem(name: "min_rating", value: String(minRating)))
            }
        }
        
        // Build query string
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }
        
        struct MapSearchResponse: Codable {
            let success: Bool
            let data: [DiveCenter]
        }
        
        let response: MapSearchResponse = try await request(endpoint: endpoint)
        return response.data
    }
    
    /// Get popular dive centers (fallback when location is unavailable)
    func getPopularDiveCenters(country: String? = nil, limit: Int = 20) async throws -> [DiveCenter] {
        var endpoint = "/api/v1/dive-centers/popular"
        var queryItems: [URLQueryItem] = []
        
        if let country = country {
            queryItems.append(URLQueryItem(name: "country", value: country))
        }
        queryItems.append(URLQueryItem(name: "limit", value: String(min(limit, 100))))
        
        if !queryItems.isEmpty {
            let queryString = queryItems.compactMap { item -> String? in
                guard let value = item.value else { return nil }
                let encodedName = item.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? item.name
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedName)=\(encodedValue)"
            }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }

        struct PopularResponse: Codable {
            let success: Bool
            let data: [DiveCenter]
        }
        
        let response: PopularResponse = try await request(endpoint: endpoint)

        return response.data
    }
    
    // MARK: - Response Models for Dive Center Geo Search
    
    struct DiveCenterSearchResult: Codable {
        let success: Bool
        let data: [DiveCenter]
        let pagination: PaginationInfo?
        let meta: SearchMeta?
    }
    
    // Bookings
    func createBooking(_ booking: Booking) async throws -> Booking {
        return try await request(endpoint: "/api/bookings", method: .post, body: booking)
    }
    
    func getBookings(userId: String? = nil) async throws -> [Booking] {
        var endpoint = "/api/bookings"
        if let userId = userId {
            endpoint += "?userId=\(userId)"
        }
        return try await request(endpoint: endpoint)
    }
    
    // Dive Logs
    struct CreateDiveLogRequest: Codable {
        let diveSiteId: String?
        let date: String // ISO date string
        let startTime: String?
        let endTime: String?
        let duration: Int // in minutes
        let maxDepth: Double
        let averageDepth: Double?
        let waterTemperature: Double?
        let visibility: Double?
        let current: String?
        let diveType: String?
        let notes: String?
        let photoUrls: [String]?
        let videoUrls: [String]?
        
        // gearUsed and diveComputerData are complex types, skip for now
    }
    
    func createDiveLog(_ log: DiveLog) async throws -> DiveLog {// Convert DiveLog to CreateDiveLogRequest DTO
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withFullDate]
        
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm"
        
        // Parse time string to create startTime if available
        // Backend expects ISO 8601 date-time format for startTime/endTime
        var startTime: String? = nil
        var endTime: String? = nil
        if !log.time.isEmpty {
            // Try to parse time in HH:mm format
            // Validate that time string matches HH:mm format (e.g., "14:30", not "565")
            let timePattern = #"^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$"#
            let regex = try? NSRegularExpression(pattern: timePattern)
            let range = NSRange(location: 0, length: log.time.utf16.count)
            
            if let regex = regex, regex.firstMatch(in: log.time, range: range) != nil,
               let timeDate = timeFormatter.date(from: log.time) {
                // Combine date and time into ISO 8601 format
                let calendar = Calendar.current
                let dateComponents = calendar.dateComponents([.year, .month, .day], from: log.date)
                let timeComponents = calendar.dateComponents([.hour, .minute], from: timeDate)
                var combinedComponents = DateComponents()
                combinedComponents.year = dateComponents.year
                combinedComponents.month = dateComponents.month
                combinedComponents.day = dateComponents.day
                combinedComponents.hour = timeComponents.hour
                combinedComponents.minute = timeComponents.minute
                
                if let combinedDate = calendar.date(from: combinedComponents) {
                    let isoFormatter = ISO8601DateFormatter()
                    isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                    startTime = isoFormatter.string(from: combinedDate)
                    
                    // Calculate endTime based on bottomTime
                    if log.bottomTime > 0 {
                        let endDate = combinedDate.addingTimeInterval(TimeInterval(log.bottomTime * 60))
                        endTime = isoFormatter.string(from: endDate)
                    }
                }
            }
            // If time format is invalid, skip startTime/endTime (they're optional)
        }
        
        // Validate required fields
        guard log.bottomTime > 0 else {
            throw NetworkError.unknown(NSError(domain: "DiveLogError", code: 400, userInfo: [NSLocalizedDescriptionKey: "Bottom time must be greater than 0"]))
        }
        
        guard log.maxDepth > 0 else {
            throw NetworkError.unknown(NSError(domain: "DiveLogError", code: 400, userInfo: [NSLocalizedDescriptionKey: "Max depth must be greater than 0"]))
        }
        
        let request = CreateDiveLogRequest(
            diveSiteId: log.diveSiteId,
            date: dateFormatter.string(from: log.date),
            startTime: startTime,
            endTime: endTime,
            duration: log.bottomTime, // Convert bottomTime to duration
            maxDepth: log.maxDepth,
            averageDepth: log.averageDepth > 0 ? log.averageDepth : nil,
            waterTemperature: log.waterTemperature,
            visibility: log.visibility,
            current: log.current,
            diveType: nil, // Not in our model yet
            notes: log.notes.isEmpty ? nil : log.notes,
            photoUrls: log.photos.isEmpty ? nil : log.photos,
            videoUrls: log.videos.isEmpty ? nil : log.videos
        )
        do {
            let result: DiveLog = try await self.request(endpoint: "/api/dive-logs", method: .post, body: request)
            return result
        } catch {
            throw error
        }
    }
    
    func getDiveLogs(userId: String) async throws -> [DiveLog] {
        return try await request(endpoint: "/api/dive-logs?userId=\(userId)")
    }
    
    // Get public dive logs for a dive site (only from users who share their logbook)
    func getPublicDiveLogsForSite(diveSiteId: String) async throws -> [DiveLog] {
        // Properly encode the query parameter
        guard let encodedDiveSiteId = diveSiteId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw NetworkError.invalidURL
        }
        
        // Use the new public endpoint
        let endpoint = "/api/dive-logs/public?diveSiteId=\(encodedDiveSiteId)"
        
        do {
            let result: [DiveLog] = try await request(endpoint: endpoint)
            return result
        } catch {
            // If 404, return empty array (no public dive logs found)
            if let networkError = error as? NetworkError,
               case .serverError(404) = networkError {
                return []
            }
            
            throw error
        }
    }
    
    // Reviews
    func createReview(_ createReviewRequest: CreateReviewRequest) async throws -> Review {
        return try await request(endpoint: "/api/reviews", method: .post, body: createReviewRequest)
    }
    
    func getReviews(reviewableType: ReviewableType, reviewableId: String) async throws -> [Review] {
        return try await request(endpoint: "/api/reviews?type=\(reviewableType.rawValue)&id=\(reviewableId)")
    }
    
    // MARK: - Feed API
    
    func getFeedPosts(cursor: String? = nil, limit: Int = 20) async throws -> FeedPage {
        var parts = ["limit=\(limit)"]
        if let cursor, let enc = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            parts.append("cursor=\(enc)")
        }
        let q = parts.joined(separator: "&")
        return try await request(endpoint: "/api/feed/posts?\(q)")
    }
    
    func createFeedPost(type: FeedPost.PostType, content: String?, diveLogId: String?, photos: [String]) async throws -> FeedPost {
        struct CreatePostRequest: Codable {
            let type: String
            let content: String?
            let diveLogId: String?
            let photos: [String]
        }
        
        let request = CreatePostRequest(
            type: type.rawValue,
            content: content,
            diveLogId: diveLogId,
            photos: photos
        )
        
        return try await self.request(endpoint: "/api/feed/posts", method: .post, body: request)
    }
    
    func togglePostLike(postId: String) async throws -> FeedPost {
        return try await request(endpoint: "/api/feed/posts/\(postId)/like", method: .post)
    }
    
    func getPostComments(postId: String) async throws -> [FeedComment] {
        return try await request(endpoint: "/api/feed/posts/\(postId)/comments")
    }
    
    func addPostComment(postId: String, content: String) async throws -> FeedComment {
        struct CommentRequest: Codable {
            let content: String
        }
        
        let request = CommentRequest(content: content)
        return try await self.request(endpoint: "/api/feed/posts/\(postId)/comments", method: .post, body: request)
    }
    
    func getProfileFeedPosts(userId: String, cursor: String? = nil, limit: Int = 20) async throws -> FeedPage {
        var parts = ["limit=\(limit)"]
        if let cursor, let enc = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            parts.append("cursor=\(enc)")
        }
        let q = parts.joined(separator: "&")
        return try await request(endpoint: "/api/feed/profile/\(userId)/posts?\(q)")
    }
    
    // MARK: - Chat API
    
    func getChatConversations() async throws -> [ChatConversation] {
        try await request(endpoint: "/api/chat/conversations")
    }
    
    func openChatConversation(peerType: String, peerId: String) async throws -> ChatConversation {
        struct OpenChatRequest: Codable {
            let peerType: String
            let peerId: String
        }
        return try await request(
            endpoint: "/api/chat/conversations",
            method: .post,
            body: OpenChatRequest(peerType: peerType, peerId: peerId)
        )
    }
    
    func getChatMessages(conversationId: String, before: String? = nil, limit: Int = 40) async throws -> ChatMessagesPage {
        var parts = ["limit=\(limit)"]
        if let before, let enc = before.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            parts.append("before=\(enc)")
        }
        let q = parts.joined(separator: "&")
        return try await request(endpoint: "/api/chat/\(conversationId)/messages?\(q)")
    }
    
    func sendChatMessage(
        conversationId: String,
        content: String,
        messageType: String = "text",
        attachments: [ChatMessage.Attachment]? = nil
    ) async throws -> ChatMessage {
        struct AttDTO: Codable {
            let type: String
            let url: String
            let thumbnailURL: String?
        }
        struct SendChatRequest: Codable {
            let conversationId: String
            let content: String?
            let messageType: String
            let attachments: [AttDTO]?
        }
        let attDTO = attachments?.map {
            AttDTO(type: $0.type.rawValue, url: $0.url, thumbnailURL: $0.thumbnailURL)
        }
        let body = SendChatRequest(
            conversationId: conversationId,
            content: content.isEmpty ? nil : content,
            messageType: messageType,
            attachments: attDTO
        )
        return try await request(endpoint: "/api/chat/messages", method: .post, body: body)
    }
    
    /// Returns absolute URL for use in posts / chat attachments.
    func uploadMediaImage(_ imageData: Data, fileName: String = "photo.jpg") async throws -> String {
        guard let url = URL(string: baseURL + "/api/media/upload") else {
            throw NetworkError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw NetworkError.unknown(NSError(domain: "Invalid response", code: -1))
        }
        guard (200...299).contains(http.statusCode) else {
            throw NetworkError.serverError(http.statusCode)
        }
        let decoded = try JSONDecoder().decode(MediaUploadResponse.self, from: data)
        if decoded.url.hasPrefix("http") {
            return decoded.url
        }
        return baseURL + decoded.url
    }
    
    func registerPushDeviceToken(_ token: String) async throws {
        struct Body: Codable {
            let token: String
            let platform: String
        }
        _ = try await request(
            endpoint: "/api/users/me/push-token",
            method: .post,
            body: Body(token: token, platform: "ios")
        ) as EmptyResponse
    }
    
    /// Chat WebSocket path is **not** under `/api`.
    func chatWebSocketURL(accessToken: String) -> URL? {
        var host = baseURL
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        while host.hasSuffix("/") {
            host.removeLast()
        }
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "+&")
        let enc = accessToken.addingPercentEncoding(withAllowedCharacters: allowed) ?? accessToken
        return URL(string: "\(host)/ws/chat?token=\(enc)")
    }
}

// MARK: - Filter Models

struct DiveSiteFilters: Codable, Sendable, Equatable {
    var siteType: DiveSiteType?
    var difficulty: DifficultyLevel?
    var minDepth: Double?
    var maxDepth: Double?
    var marineLife: [String]?
    var minRating: Double?
    var maxDistance: Double? // in km (for legacy API)
    var centerLatitude: Double?
    var centerLongitude: Double?
    var country: String? // For geo search
    var accessTypes: [String]? // ["shore", "boat"]
    
    /// Returns radius in meters for geo search (converts maxDistance from km)
    nonisolated var radiusMeters: Int {
        if let maxDistance = maxDistance {
            return Int(maxDistance * 1000) // Convert km to meters
        }
        return 500000 // Default 500km (increased for large regions like Red Sea)
    }
    
    /// Returns true if geo search should be used (has location and not using global search)
    nonisolated var shouldUseGeoSearch: Bool {
        // Don't use geo search if maxDistance is explicitly set to nil (global search)
        if maxDistance == nil && centerLatitude == nil && centerLongitude == nil {
            return false
        }
        // Use geo search if location is available and maxDistance is not nil
        return centerLatitude != nil && centerLongitude != nil && maxDistance != nil
    }
}

struct DiveCenterFilters: Codable, Sendable {
    var city: String?
    var country: String?
    var minRating: Double?
    var serviceType: Service.ServiceType?
    var certificationAgency: String?
    var languages: [String]?
    var nitroxAvailable: Bool?
    var maxPrice: Double?
    var maxDistance: Double?
    var centerLatitude: Double?
    var centerLongitude: Double?
}

struct ShopFilters: Codable, Sendable {
    var shopType: ShopType?
    var brands: [String]?
    var serviceAvailable: Bool?
    var minRating: Double?
    var maxDistance: Double?
    var centerLatitude: Double?
    var centerLongitude: Double?
}

extension NetworkService {
    // Localization
    func getTranslations(language: String) async throws -> [String: [String: String]] {
        return try await request(endpoint: "/api/localization/\(language)")
    }
    
    // Countries
    func getCountries() async throws -> [String] {
        let countries = try await getCountriesFull()
        
        // Extract country names with localization support
        let countryNames = countries.map { $0.displayName }.sorted()
        
        return countryNames
    }
    
    func getCountriesFromDiveSites() async throws -> (success: Bool, data: [String]) {
        struct Response: Decodable {
            let success: Bool
            let data: [String]
        }
        let response: Response = try await request(endpoint: "/api/v1/dive-sites/countries")
        return (response.success, response.data)
    }
    
    func getCountriesFull() async throws -> [Country] {
        struct CountryResponse: Codable {
            let id: String
            let name: String
            let localizedNames: [String: String]?
            let regions: [RegionResponse]?
            
            struct RegionResponse: Codable {
                let name: String
                let localizedNames: [String: String]?
            }
        }
        
        let countriesResponse: [CountryResponse] = try await request(endpoint: "/api/countries")
        
        // Convert to Country objects
        let countries = countriesResponse.map { response -> Country in
            let regions = response.regions?.map { regionResponse -> Country.Region in
                Country.Region(
                    name: regionResponse.name,
                    localizedNames: regionResponse.localizedNames ?? [:]
                )
            }
            
            return Country(
                id: response.id,
                name: response.name,
                localizedNames: response.localizedNames ?? [:],
                regions: regions
            )
        }
        
        return countries
    }
    
    func getRegions(country: String) async throws -> [String] {
        struct RegionResponse: Codable {
            let id: String
            let name: String
            let localizedNames: [String: String]?
        }
        
        // Encode country name for URL
        guard let encodedCountry = country.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw NetworkError.invalidURL
        }
        
        let regionsResponse: [RegionResponse] = try await request(endpoint: "/api/regions?country=\(encodedCountry)")
        
        // Get current language from LocalizationService
        let currentLanguage = LocalizationService.shared.currentLanguage.rawValue
        
        // Extract region names with localization support
        let regionNames = regionsResponse.map { region -> String in
            // Try to get localized name first, fallback to default name
            if let localizedNames = region.localizedNames,
               let localizedName = localizedNames[currentLanguage] {
                return localizedName
            }
            return region.name
        }.sorted()
        
        return regionNames
    }
    
    // Translation
    func translateText(text: String, from sourceLanguage: String, to targetLanguage: String) async throws -> String {
        struct TranslateRequest: Codable {
            let text: String
            let sourceLanguage: String
            let targetLanguage: String
        }
        
        struct TranslateResponse: Codable {
            let translatedText: String
        }
        
        let request = TranslateRequest(text: text, sourceLanguage: sourceLanguage, targetLanguage: targetLanguage)
        let response: TranslateResponse = try await self.request(
            endpoint: "/api/translate",
            method: .post,
            body: request
        )
        return response.translatedText
    }
    
    func translateTextBatch(texts: [String], from sourceLanguage: String, to targetLanguage: String) async throws -> [String] {
        struct TranslateBatchRequest: Codable {
            let texts: [String]
            let sourceLanguage: String
            let targetLanguage: String
        }
        
        struct TranslateBatchResponse: Codable {
            let translatedTexts: [String]
        }
        
        let request = TranslateBatchRequest(texts: texts, sourceLanguage: sourceLanguage, targetLanguage: targetLanguage)
        let response: TranslateBatchResponse = try await self.request(
            endpoint: "/api/translate/batch",
            method: .post,
            body: request
        )
        return response.translatedTexts
    }
    
    // Admin API
    func getCenterBookings(centerId: String? = nil) async throws -> [Booking] {
        var endpoint = "/api/admin/bookings"
        if let centerId = centerId {
            endpoint += "?centerId=\(centerId)"
        }
        return try await request(endpoint: endpoint)
    }
    
    func updateBookingStatus(bookingId: String, status: Booking.BookingStatus) async throws -> Booking {
        struct UpdateRequest: Codable {
            let status: String
        }
        let request = UpdateRequest(status: status.rawValue)
        return try await self.request(
            endpoint: "/api/admin/bookings/\(bookingId)/status",
            method: .patch,
            body: request
        )
    }
    
    func getCenterGear(centerId: String) async throws -> [GearItem] {
        return try await request(endpoint: "/api/admin/centers/\(centerId)/gear")
    }
    
    func updateGearStatus(gearId: String, status: GearItem.GearStatus) async throws -> GearItem {
        struct UpdateRequest: Codable {
            let status: String
        }
        let request = UpdateRequest(status: status.rawValue)
        return try await self.request(
            endpoint: "/api/admin/gear/\(gearId)/status",
            method: .patch,
            body: request
        )
    }
    
    func getCenterInstructors(centerId: String) async throws -> [User] {
        return try await request(endpoint: "/api/admin/centers/\(centerId)/instructors")
    }
    
    func getAdminErrorStats() async throws -> AdminViewModel.ErrorStats {
        return try await request(endpoint: "/api/admin/error-stats")
    }
    
    // Instructor API
    func getInstructorBookings(instructorId: String? = nil) async throws -> [Booking] {
        // Backend uses current user's ID from JWT token, so we don't need to pass instructorId
        let endpoint = "/api/instructor/bookings"
        return try await request(endpoint: endpoint)
    }
    
    func markDiveCompleted(bookingId: String) async throws -> Booking {
        return try await request(
            endpoint: "/api/instructor/bookings/\(bookingId)/complete",
            method: .post
        )
    }
    
    // Social API
    func searchUsers(query: String) async throws -> [User] {
        let endpoint = "/api/users/search?query=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)"
        do {
            let results: [User] = try await request(endpoint: endpoint)
            return results
        } catch {
            throw error
        }
    }
    
    func getUser(userId: String) async throws -> User {
        return try await request(endpoint: "/api/users/\(userId)")
    }
    
    func sendFriendRequest(userId: String) async throws {struct FriendRequest: Codable {
            let userId: String
        }
        let request = FriendRequest(userId: userId)
        do {
            _ = try await self.request(
                endpoint: "/api/friends/requests",
                method: .post,
                body: request
            ) as EmptyResponse} catch let error as NetworkError {// Re-throw with more context if it's a 400 error
            if case .serverError(400) = error {
                throw FriendRequestError.alreadyExists
            }
            throw error
        } catch {throw error
        }
    }
    
    func getFriends() async throws -> [User] {
        return try await request(endpoint: "/api/friends")
    }
    
    func acceptFriendRequest(userId: String) async throws {
        _ = try await self.request(
            endpoint: "/api/friends/requests/\(userId)/accept",
            method: .post
        ) as EmptyResponse
    }
    
    struct FriendRequestResponse: Codable {
        let id: String
        let user: User
        let createdAt: Date
    }
    
    func getSentFriendRequests() async throws -> [FriendRequestResponse] {
        return try await request(endpoint: "/api/friends/requests/sent")
    }
    
    func getReceivedFriendRequests() async throws -> [FriendRequestResponse] {
        return try await request(endpoint: "/api/friends/requests/received")
    }
    
    func declineFriendRequest(friendshipId: String) async throws {
        _ = try await self.request(
            endpoint: "/api/friends/requests/\(friendshipId)",
            method: .delete
        ) as EmptyResponse
    }
    
    // Profile Image Upload
    func uploadProfileImage(imageData: Data) async throws -> String {
        guard let url = URL(string: baseURL + "/api/users/me/avatar") else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        
        // Create multipart form data
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        
        request.httpBody = body
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.unknown(NSError(domain: "Invalid response", code: -1))
        }
        
        if httpResponse.statusCode == 401 {
            // Try to refresh token
            let newAccessToken = try await refreshAccessToken()
            request.setValue("Bearer \(newAccessToken)", forHTTPHeaderField: "Authorization")
            let (retryData, retryResponse) = try await session.data(for: request)
            guard let retryHttpResponse = retryResponse as? HTTPURLResponse else {
                throw NetworkError.unknown(NSError(domain: "Invalid response", code: -1))
            }
            guard (200...299).contains(retryHttpResponse.statusCode) else {
                throw NetworkError.serverError(retryHttpResponse.statusCode)
            }
            
            struct UploadResponse: Codable {
                let avatarUrl: String
            }
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let uploadResponse = try decoder.decode(UploadResponse.self, from: retryData)
            
            // Convert relative URL to absolute URL if needed
            var avatarUrl = uploadResponse.avatarUrl
            if avatarUrl.hasPrefix("/") && !avatarUrl.hasPrefix("http") {
                avatarUrl = baseURL + avatarUrl
            }
            return avatarUrl
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.serverError(httpResponse.statusCode)
        }
        
        struct UploadResponse: Codable {
            let avatarUrl: String
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let uploadResponse = try decoder.decode(UploadResponse.self, from: data)
        
        // Convert relative URL to absolute URL if needed
        var avatarUrl = uploadResponse.avatarUrl
        if avatarUrl.hasPrefix("/") && !avatarUrl.hasPrefix("http") {
            avatarUrl = baseURL + avatarUrl
        }
        return avatarUrl
    }
    
    // MARK: - Certifications API
    
    func getCertifications(userId: String) async throws -> [Certification] {
        let endpoint = "/api/users/\(userId)/certifications"
        return try await request(endpoint: endpoint)
    }
    
    func createCertification(userId: String, certification: Certification, cardImageData: Data?) async throws -> Certification {
        // First upload image if provided
        var cardImageUrl: String? = nil
        if let imageData = cardImageData {
            do {
                cardImageUrl = try await uploadCertificationImage(imageData: imageData)
            } catch {
                // Continue without image if upload fails
            }
        }
        
        // Create certification request matching backend DTO
        struct CreateCertificationRequest: Codable {
            let agency: String
            let level: String
            let issueDate: String
            let instructorNumber: String?
            let cardImageUrl: String?
            let verificationStatus: String?
        }
        
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        let request = CreateCertificationRequest(
            agency: certification.organization,
            level: certification.level,
            issueDate: formatter.string(from: certification.issueDate ?? Date()),
            instructorNumber: certification.instructorNumber,
            cardImageUrl: cardImageUrl,
            verificationStatus: certification.verificationStatus.rawValue
        )
        
        let endpoint = "/api/users/\(userId)/certifications"
        return try await self.request(endpoint: endpoint, method: .post, body: request)
    }
    
    func deleteCertification(certificationId: String) async throws {
        let endpoint = "/api/users/certifications/\(certificationId)"
        let _: EmptyResponse = try await request(endpoint: endpoint, method: .delete)
    }
    
    private func uploadCertificationImage(imageData: Data) async throws -> String {
        // Use the same avatar upload endpoint for now
        // The endpoint returns avatarUrl, which we can use for certification images
        return try await uploadProfileImage(imageData: imageData)
    }
    
    // MARK: - Media Upload
    
    func uploadImage(imageData: Data) async throws -> String {
        guard let url = URL(string: baseURL + "/api/media/upload") else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        
        // Create multipart form data
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        
        request.httpBody = body
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.unknown(NSError(domain: "Invalid response", code: -1))
        }
        if httpResponse.statusCode == 401 {
            // Try to refresh token
            let newAccessToken = try await refreshAccessToken()
            request.setValue("Bearer \(newAccessToken)", forHTTPHeaderField: "Authorization")
            let (retryData, retryResponse) = try await session.data(for: request)
            guard let retryHttpResponse = retryResponse as? HTTPURLResponse else {
                throw NetworkError.unknown(NSError(domain: "Invalid response", code: -1))
            }
            guard (200...299).contains(retryHttpResponse.statusCode) else {
                throw NetworkError.serverError(retryHttpResponse.statusCode)
            }
            
            struct UploadResponse: Codable {
                let url: String
            }
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let uploadResponse = try decoder.decode(UploadResponse.self, from: retryData)
            
            // Convert relative URL to absolute URL if needed
            var imageUrl = uploadResponse.url
            if imageUrl.hasPrefix("/") && !imageUrl.hasPrefix("http") {
                imageUrl = baseURL + imageUrl
            }
            
            return imageUrl
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.serverError(httpResponse.statusCode)
        }
        
        struct UploadResponse: Codable {
            let url: String
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let uploadResponse = try decoder.decode(UploadResponse.self, from: data)
        
        // Convert relative URL to absolute URL if needed
        var imageUrl = uploadResponse.url
        if imageUrl.hasPrefix("/") && !imageUrl.hasPrefix("http") {
            imageUrl = baseURL + imageUrl
        }
        return imageUrl
    }
    
    // MARK: - Underwater AI (backend image enhancement)
    
    /// Check if backend AI underwater service is available.
    func isUnderwaterAIAvailable() async -> Bool {
        guard let url = URL(string: baseURL + "/api/v1/underwater-ai/health") else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return false }
            struct Health: Codable { let available: Bool }
            let health = try JSONDecoder().decode(Health.self, from: data)
            return health.available
        } catch {
            return false
        }
    }
    
    /// Process underwater photo via backend AI. Returns JPEG data or throws.
    func processUnderwaterPhotoWithAI(
        imageData: Data,
        depthMeters: Double = 10,
        strength: Double = 0.7,
        useAi: Bool = true,
        pipeline: String = "default"
    ) async throws -> Data {
        let processPath = "/api/v1/underwater-ai/process"
        let fullURLString = baseURL + processPath
        guard let url = URL(string: fullURLString) else {
            throw NetworkError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"depth_m\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(depthMeters)\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"strength\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(strength)\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"use_ai\"\r\n\r\n".data(using: .utf8)!)
        body.append((useAi ? "true" : "false").data(using: .utf8)!)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"pipeline\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(pipeline)\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let config = URLSessionConfiguration.default
        let pl = pipeline.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let heavy = (pl == "jmse1820" || pl == "article3" || pl == "gpt")
        config.timeoutIntervalForRequest = heavy ? 180 : 90
        config.timeoutIntervalForResource = heavy ? 300 : 120
        let longSession = URLSession(configuration: config)
        let (data, response) = try await longSession.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else { throw NetworkError.serverError(http.statusCode) }
        return data
    }
    
    // MARK: - Underwater Vision Module (Python FastAPI, standalone)
    
    /// UserDefaults key; if empty, uses default host in DEBUG.
    static let underwaterVisionModuleBaseURLKey = "underwaterVisionModuleBaseURL"
    
    /// Base URL без завершающего `/`.
    /// Пусто в DEBUG → прямой UVM `http://127.0.0.1:8010`.
    /// Пусто в Release → тот же хост, что и REST API (Nest проксирует на UVM через `UVM_URL`).
    static func underwaterVisionModuleBaseURLString() -> String {
        let raw = UserDefaults.standard.string(forKey: underwaterVisionModuleBaseURLKey) ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            return trimmed.hasSuffix("/") ? String(trimmed.dropLast()) : trimmed
        }
        #if DEBUG
        return "http://127.0.0.1:8010"
        #else
        let api = UserDefaults.standard.string(forKey: apiBaseURLUserDefaultsKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !api.isEmpty {
            return api.hasSuffix("/") ? String(api.dropLast()) : api
        }
        return Self.productionAPIBaseURL
        #endif
    }
    
    /// `true` если клиент ходит напрямую на uvicorn (порт 8010), иначе — фасад Nest `/api/v1/process/photo/...`.
    private static func isDirectUnderwaterVisionHost(_ base: String) -> Bool {
        let b = base.trimmingCharacters(in: .whitespacesAndNewlines)
        if b.hasSuffix(":8010") { return true }
        guard let u = URL(string: b) else { return b.contains(":8010") }
        if u.port == 8010 { return true }
        return false
    }
    
    private static func underwaterVisionProcessPhotoURL(base: String, engine: String, queryItems: [URLQueryItem]) -> URL? {
        let trimmed = base.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = trimmed.hasSuffix("/") ? String(trimmed.dropLast()) : trimmed
        let pathEngine = engine.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? engine
        let fullPath: String
        if Self.isDirectUnderwaterVisionHost(normalized) {
            fullPath = "\(normalized)/v1/process/photo/\(pathEngine)"
        } else {
            let withApi = normalized.hasSuffix("/api") ? normalized : "\(normalized)/api"
            fullPath = "\(withApi)/v1/process/photo/\(pathEngine)"
        }
        var c = URLComponents(string: fullPath)
        c?.queryItems = queryItems
        return c?.url
    }
    
    /// `GET /health` (прямой UVM) или `GET /api/health` (Nest).
    func checkUnderwaterVisionModuleHealth() async -> Bool {
        let base = Self.underwaterVisionModuleBaseURLString()
        let path: String
        if Self.isDirectUnderwaterVisionHost(base) {
            path = base + "/health"
        } else {
            let withApi = base.hasSuffix("/api") ? base : base + "/api"
            path = withApi + "/health"
        }
        guard let url = URL(string: path) else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 6
        do {
            let (_, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return false }
            return true
        } catch {
            return false
        }
    }
    
    /// Локаль для query (иначе на ru_RU iPhone получится `0,700000` и сервер не распарсит числа → 422/400).
    private static let underwaterVisionQueryLocale = Locale(identifier: "en_US_POSIX")
    
    /// Разбор тела ошибки `POST /v1/process/photo/...` (FastAPI `detail`, UVM `error`).
    private static func underwaterVisionErrorMessage(from data: Data) -> String? {
        guard !data.isEmpty else { return nil }
        guard let obj = try? JSONSerialization.jsonObject(with: data) else { return nil }
        if let dict = obj as? [String: Any] {
            if let s = dict["detail"] as? String, !s.isEmpty { return s }
            if let s = dict["error"] as? String, !s.isEmpty {
                if let allowed = dict["allowed"] {
                    return "\(s) (allowed: \(allowed))"
                }
                return s
            }
            if let arr = dict["detail"] as? [[String: Any]],
               let msg = arr.compactMap({ $0["msg"] as? String }).first {
                return msg
            }
            if let arr = dict["detail"] as? [String], let first = arr.first, !first.isEmpty {
                return first
            }
            if let msg = dict["message"] as? String, !msg.isEmpty { return msg }
        }
        return nil
    }
    
    /// Если JSON не разобрался — показываем сырой текст/HTML, чтобы на iPhone было видно прокси/Nest/пустой ответ.
    private static func underwaterVisionErrorFallback(data: Data) -> String {
        if data.isEmpty {
            return "empty body — check Underwater module URL (must be UVM/FastAPI with /v1/process/photo, not only Nest :3000)"
        }
        guard let raw = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty else {
            return "non‑UTF8 body (\(data.count) bytes)"
        }
        if raw.hasPrefix("<") || raw.localizedCaseInsensitiveContains("<html") {
            return "HTML error page — host may be Nest or CDN, not Python UVM on 8010"
        }
        return String(raw.prefix(400))
    }
    
    /// `POST /v1/process/photo/{engine}` — multipart только `image`; `strength` / `depth_hint_m` в query (engine в path — надёжно с multipart).
    func processPhotoUnderwaterVisionModule(
        imageJPEG: Data,
        engine: String,
        strength: Double = 0.7,
        depthHintMeters: Double? = nil
    ) async throws -> Data {
        let base = Self.underwaterVisionModuleBaseURLString()
        var items: [URLQueryItem] = [
            URLQueryItem(
                name: "strength",
                value: String(format: "%.6f", locale: Self.underwaterVisionQueryLocale, strength)
            )
        ]
        if let d = depthHintMeters {
            items.append(
                URLQueryItem(
                    name: "depth_hint_m",
                    value: String(format: "%.6f", locale: Self.underwaterVisionQueryLocale, d)
                )
            )
        }
        guard let url = Self.underwaterVisionProcessPhotoURL(base: base, engine: engine, queryItems: items) else {
            throw NetworkError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        // Только файл — engine/strength в query (сервер надёжно различает ai1/ai2/cursor)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageJPEG)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let config = URLSessionConfiguration.default
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.urlCache = nil
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 180
        let longSession = URLSession(configuration: config)
        let (data, response) = try await longSession.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else {
            let parsed = Self.underwaterVisionErrorMessage(from: data)
            let msg = parsed ?? Self.underwaterVisionErrorFallback(data: data)
            throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: msg)
        }
        var serverReportEngine: String = ""
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let rep = obj["report"] as? [String: Any],
           let re = rep["engine"] as? String {
            serverReportEngine = re
        }
        if !serverReportEngine.isEmpty,
           serverReportEngine.lowercased() != engine.lowercased() {
            throw NetworkError.visionModuleHTTPError(
                statusCode: 502,
                message: "Engine mismatch: requested '\(engine)', server used '\(serverReportEngine)'. Check UVM_URL/proxy target."
            )
        }
        struct UVMEnvelope: Decodable {
            let image_jpeg_base64: String
        }
        let env = try JSONDecoder().decode(UVMEnvelope.self, from: data)
        guard let jpeg = Data(hexEncoded: env.image_jpeg_base64) else {
            throw NetworkError.decodingError
        }
        return jpeg
    }

    /// `POST /v1/process/video/{engine}` — multipart `video`; returns processed mp4 bytes.
    /// При `progress != nil` прогресс строится по байтам upload/download и по времени ожидания сервера (оценка от `sourceVideoDuration`).
    func processVideoUnderwaterVisionModule(
        videoData: Data,
        engine: String,
        strength: Double = 0.7,
        depthHintMeters: Double? = nil,
        sourceVideoDuration: TimeInterval? = nil,
        progress: (@MainActor (VideoUnderwaterProcessingProgress) -> Void)? = nil
    ) async throws -> Data {
        let base = Self.underwaterVisionModuleBaseURLString()
        var items: [URLQueryItem] = [
            URLQueryItem(
                name: "strength",
                value: String(format: "%.6f", locale: Self.underwaterVisionQueryLocale, strength)
            )
        ]
        if let d = depthHintMeters {
            items.append(
                URLQueryItem(
                    name: "depth_hint_m",
                    value: String(format: "%.6f", locale: Self.underwaterVisionQueryLocale, d)
                )
            )
        }
        items.append(
            URLQueryItem(
                name: "luma_boost",
                value: String(format: "%.4f", locale: Self.underwaterVisionQueryLocale, 1.18)
            )
        )
        items.append(URLQueryItem(name: "max_side", value: "1280"))
        let trimmed = base.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = trimmed.hasSuffix("/") ? String(trimmed.dropLast()) : trimmed
        let pathEngine = engine.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? engine
        let fullPath: String
        if Self.isDirectUnderwaterVisionHost(normalized) {
            fullPath = "\(normalized)/v1/process/video/\(pathEngine)"
        } else {
            let withApi = normalized.hasSuffix("/api") ? normalized : "\(normalized)/api"
            fullPath = "\(withApi)/v1/process/video/\(pathEngine)"
        }
        var c = URLComponents(string: fullPath)
        c?.queryItems = items
        guard let url = c?.url else { throw NetworkError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        // Иначе срабатывает дефолт URLRequest (~60 с) и показывается «The request timed out» при долгой обработке на сервере.
        request.timeoutInterval = UnderwaterVisionVideoTimeouts.resourceSeconds()
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"video\"; filename=\"video.mp4\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: video/mp4\r\n\r\n".data(using: .utf8)!)
        body.append(videoData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        if progress == nil {
            request.httpBody = body
            let config = URLSessionConfiguration.default
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            config.urlCache = nil
            config.timeoutIntervalForRequest = UnderwaterVisionVideoTimeouts.requestSeconds()
            config.timeoutIntervalForResource = UnderwaterVisionVideoTimeouts.resourceSeconds()
            let longSession = URLSession(configuration: config)
            let (data, response) = try await longSession.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
            guard (200...299).contains(http.statusCode) else {
                let parsed = Self.underwaterVisionErrorMessage(from: data)
                let msg = parsed ?? Self.underwaterVisionErrorFallback(data: data)
                throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: msg)
            }
            return data
        }

        let delegate = UVMVideoUploadSessionDelegate(
            uploadBody: body,
            sourceVideoDuration: sourceVideoDuration,
            onProgress: progress
        )
        return try await delegate.perform(uploadRequest: request)
    }

    /// URLSession delegate: upload progress, тикер пока сервер молчит, download progress, ETA.
    private final class UVMVideoUploadSessionDelegate: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate {
        private let uploadBody: Data
        private let sourceVideoDuration: TimeInterval
        private let onProgress: (@MainActor (VideoUnderwaterProcessingProgress) -> Void)?

        private let lock = NSLock()
        private var aggregated = Data()
        private var sentBytes: Int64 = 0
        private var totalSendBytes: Int64
        private var sendComplete = false
        private var sendDoneAt: Date?
        private var respTotal: Int64 = -1
        private var gotBytes: Int64 = 0
        private var receiving = false
        private var statusCode: Int?
        private let t0 = Date()

        private var continuation: CheckedContinuation<Data, Error>?
        private var urlSession: URLSession?
        private var tickTask: Task<Void, Never>?
        /// Временный файл для upload: с `Data` URLSession часто не шлёт `didSendBodyData` → прогресс 0% до ответа сервера.
        private var tempUploadURL: URL?
        private weak var uploadTaskRef: URLSessionTask?
        private var gotResponseHeaders = false
        private var headerStallWatchdog: Task<Void, Never>?
        private var bodyStallWatchdog: Task<Void, Never>?
        private var canceledDueToStall = false

        init(uploadBody: Data, sourceVideoDuration: TimeInterval?, onProgress: (@MainActor (VideoUnderwaterProcessingProgress) -> Void)?) {
            self.uploadBody = uploadBody
            self.totalSendBytes = Int64(uploadBody.count)
            let d = sourceVideoDuration ?? max(30, Double(uploadBody.count) / 500_000)
            self.sourceVideoDuration = max(1, d)
            self.onProgress = onProgress
            super.init()
        }

        func perform(uploadRequest: URLRequest) async throws -> Data {
            try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Data, Error>) in
                self.continuation = cont
                let cfg = URLSessionConfiguration.default
                cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
                cfg.urlCache = nil
                cfg.timeoutIntervalForRequest = UnderwaterVisionVideoTimeouts.requestSeconds()
                cfg.timeoutIntervalForResource = UnderwaterVisionVideoTimeouts.resourceSeconds()
                let session = URLSession(configuration: cfg, delegate: self, delegateQueue: nil)
                self.urlSession = session
                let tmp = FileManager.default.temporaryDirectory
                    .appendingPathComponent("uvm_video_upload_\(UUID().uuidString).mp4")
                do {
                    try self.uploadBody.write(to: tmp, options: .atomic)
                    self.tempUploadURL = tmp
                } catch {
                    cont.resume(throwing: error)
                    return
                }
                self.startProgressTicker()
                let up = session.uploadTask(with: uploadRequest, fromFile: tmp)
                self.uploadTaskRef = up
                up.resume()
                self.emitProgress()
            }
        }

        private func finishSession() {
            cancelStallWatchdogs()
            tickTask?.cancel()
            tickTask = nil
            urlSession?.finishTasksAndInvalidate()
            urlSession = nil
            uploadTaskRef = nil
            if let u = tempUploadURL {
                try? FileManager.default.removeItem(at: u)
                tempUploadURL = nil
            }
        }

        /// Тикер на весь запрос (включая скачивание): иначе при зависании после заголовков UI замирает на ~82%.
        private func startProgressTicker() {
            tickTask?.cancel()
            tickTask = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 280_000_000)
                    guard let self else { return }
                    self.emitProgress()
                }
            }
        }

        private func cancelStallWatchdogs() {
            headerStallWatchdog?.cancel()
            headerStallWatchdog = nil
            bodyStallWatchdog?.cancel()
            bodyStallWatchdog = nil
        }

        /// После окончания upload нет HTTP-заголовков ответа — обрываем, иначе «вечная» 82%.
        private func scheduleHeaderStallWatchdog() {
            headerStallWatchdog?.cancel()
            let dur = sourceVideoDuration
            headerStallWatchdog = Task { [weak self] in
                let wait = UnderwaterVisionVideoTimeouts.headerStallWaitSeconds(sourceVideoDuration: dur)
                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                guard let self, !Task.isCancelled else { return }
                let stuck = self.lock.withLock {
                    self.sendComplete && !self.gotResponseHeaders
                }
                if stuck {
                    self.lock.withLock {
                        self.canceledDueToStall = true
                    }
                    self.uploadTaskRef?.cancel()
                }
            }
        }

        /// Заголовки есть, тело ещё не пошло — отдельный лимит (сервер завис после 200).
        private func scheduleBodyStallWatchdog() {
            bodyStallWatchdog?.cancel()
            let dur = sourceVideoDuration
            bodyStallWatchdog = Task { [weak self] in
                let wait = UnderwaterVisionVideoTimeouts.bodyStallWaitSeconds(sourceVideoDuration: dur)
                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                guard let self, !Task.isCancelled else { return }
                let stuck = self.lock.withLock {
                    self.gotResponseHeaders && !self.receiving
                }
                if stuck {
                    self.lock.withLock {
                        self.canceledDueToStall = true
                    }
                    self.uploadTaskRef?.cancel()
                }
            }
        }

        private func emitProgress() {
            guard onProgress != nil else { return }
            lock.lock()
            let elapsedUpload = Date().timeIntervalSince(t0)
            var upFrac = Double(sentBytes) / Double(max(totalSendBytes, 1))
            if !sendComplete, !receiving, sentBytes == 0, elapsedUpload > 0.25 {
                let estSec = max(6.0, Double(totalSendBytes) / 250_000.0)
                upFrac = min(0.98, elapsedUpload / estSec)
            }
            var p = min(1, upFrac) * 0.12
            if p < 0.02, !sendComplete, !receiving, elapsedUpload > 0.12 {
                p = 0.02
            }
            let pastUpload = sendComplete || receiving
            if pastUpload {
                if receiving, respTotal > 0 {
                    let dr = Double(gotBytes) / Double(respTotal)
                    p = 0.82 + min(0.18, max(0, dr) * 0.18)
                } else if receiving {
                    p = max(p, 0.88)
                } else {
                    let from = sendDoneAt ?? t0
                    let elapsedPhase = Date().timeIntervalSince(from)
                    let estProc = max(15.0, sourceVideoDuration * 2.2)
                    let linear = min(0.70, (elapsedPhase / estProc) * 0.70)
                    p = 0.12 + linear
                    if linear >= 0.70 - 0.0001 {
                        let over = max(0, elapsedPhase - estProc)
                        p = 0.82 + min(0.12, over / max(120, estProc * 2.2) * 0.12)
                    }
                }
            }
            p = min(0.995, max(0, p))
            let elapsed = Date().timeIntervalSince(t0)
            // Не использовать «elapsed / p» при малом p (пол 2% из-за floor) — получалось «277:50 left».
            let etaUncapped: TimeInterval
            if receiving {
                if respTotal > 0, gotBytes < respTotal {
                    let rate = max(Double(gotBytes) / max(elapsed, 0.25), 80_000)
                    etaUncapped = Double(respTotal - gotBytes) / rate
                } else {
                    etaUncapped = max(10, sourceVideoDuration * 0.25)
                }
            } else if !sendComplete {
                let remB = max(0, totalSendBytes - sentBytes)
                let uploadLeft = Double(remB) / 200_000
                let processEst = max(25, sourceVideoDuration * 2.2)
                etaUncapped = uploadLeft + processEst
            } else {
                let sinceSend = Date().timeIntervalSince(sendDoneAt ?? t0)
                let processEst = max(25, sourceVideoDuration * 2.2)
                let frac = min(1, sinceSend / max(1, processEst * 1.2))
                etaUncapped = max(8, processEst * (1 - frac) + 20)
            }
            let etaCap = min(7200, max(90, sourceVideoDuration * 4 + Double(totalSendBytes) / 120_000 + 60))
            let eta = min(etaCap, max(0, etaUncapped))
            lock.unlock()
            let snap = VideoUnderwaterProcessingProgress(fraction01: p, estimatedSecondsRemaining: eta)
            Task { @MainActor in
                onProgress?(snap)
            }
        }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            didSendBodyData bytesSent: Int64,
            totalBytesSent: Int64,
            totalBytesExpectedToSend: Int64
        ) {
            lock.lock()
            sentBytes = totalBytesSent
            if totalBytesExpectedToSend > 0 {
                totalSendBytes = totalBytesExpectedToSend
            }
            let done = totalBytesExpectedToSend > 0 && totalBytesSent >= totalBytesExpectedToSend
            if done, !sendComplete {
                sendComplete = true
                sendDoneAt = Date()
                lock.unlock()
                scheduleHeaderStallWatchdog()
                emitProgress()
                return
            }
            lock.unlock()
            emitProgress()
        }

        func urlSession(
            _ session: URLSession,
            dataTask: URLSessionDataTask,
            didReceive response: URLResponse,
            completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
        ) {
            lock.lock()
            gotResponseHeaders = true
            if !sendComplete {
                sendComplete = true
                sendDoneAt = Date()
            }
            if let h = response as? HTTPURLResponse {
                statusCode = h.statusCode
            }
            let exp = response.expectedContentLength
            if exp > 0 {
                respTotal = exp
            }
            lock.unlock()
            headerStallWatchdog?.cancel()
            headerStallWatchdog = nil
            scheduleBodyStallWatchdog()
            emitProgress()
            completionHandler(.allow)
        }

        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            bodyStallWatchdog?.cancel()
            bodyStallWatchdog = nil
            lock.lock()
            receiving = true
            aggregated.append(data)
            gotBytes += Int64(data.count)
            lock.unlock()
            emitProgress()
        }

        func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
            lock.lock()
            let stalled = canceledDueToStall
            canceledDueToStall = false
            lock.unlock()
            finishSession()
            if stalled {
                let msg = LocalizationService.shared.localizedString("videoProcessingStallTimeout", table: "imageEditing")
                continuation?.resume(throwing: NetworkError.visionModuleHTTPError(statusCode: 0, message: msg))
                continuation = nil
                return
            }
            if let error {
                continuation?.resume(throwing: error)
                continuation = nil
                return
            }
            lock.lock()
            let data = aggregated
            let code = statusCode ?? 0
            lock.unlock()
            if !(200...299).contains(code) {
                let parsed = NetworkService.underwaterVisionErrorMessage(from: data)
                let msg = parsed ?? NetworkService.underwaterVisionErrorFallback(data: data)
                continuation?.resume(throwing: NetworkError.visionModuleHTTPError(statusCode: code, message: msg))
            } else {
                Task { @MainActor in
                    onProgress?(VideoUnderwaterProcessingProgress(fraction01: 1, estimatedSecondsRemaining: 0))
                }
                continuation?.resume(returning: data)
            }
            continuation = nil
        }
    }

    struct SeaSplatUploadSceneResponse: Decodable {
        let scene_id: String
        let frame_count: Int
        let status: String
    }

    struct SeaSplatRunJobResponse: Decodable {
        let job_id: String
        let status: String
        let scene_id: String?
    }

    struct SeaSplatJobStatusResponse: Decodable {
        let job_id: String
        let scene_id: String
        let status: String
        let progress: Double?
        let error: String?
    }

    private static func underwaterVisionURL(
        base: String,
        directPath: String,
        proxiedPath: String,
        queryItems: [URLQueryItem] = []
    ) -> URL? {
        let trimmed = base.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = trimmed.hasSuffix("/") ? String(trimmed.dropLast()) : trimmed
        let fullPath: String
        if Self.isDirectUnderwaterVisionHost(normalized) {
            fullPath = "\(normalized)\(directPath)"
        } else {
            let withApi = normalized.hasSuffix("/api") ? normalized : "\(normalized)/api"
            fullPath = "\(withApi)\(proxiedPath)"
        }
        var c = URLComponents(string: fullPath)
        c?.queryItems = queryItems.isEmpty ? nil : queryItems
        return c?.url
    }

    /// Uploads a SeaSplat scene as a set of frames.
    func uploadSeaSplatScene(imagesJPEG: [Data], posesJSON: String? = nil) async throws -> SeaSplatUploadSceneResponse {
        guard !imagesJPEG.isEmpty else { throw NetworkError.noData }
        let base = Self.underwaterVisionModuleBaseURLString()
        var q: [URLQueryItem] = []
        if let posesJSON, !posesJSON.isEmpty {
            q.append(URLQueryItem(name: "poses_json", value: posesJSON))
        }
        guard let url = Self.underwaterVisionURL(
            base: base,
            directPath: "/v1/seasplat/scenes",
            proxiedPath: "/v1/seasplat/scenes",
            queryItems: q
        ) else {
            throw NetworkError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        for (idx, jpeg) in imagesJPEG.enumerated() {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"images\"; filename=\"frame_\(idx).jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(jpeg)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else {
            let parsed = Self.underwaterVisionErrorMessage(from: data) ?? Self.underwaterVisionErrorFallback(data: data)
            throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: parsed)
        }
        guard let decoded = try? JSONDecoder().decode(SeaSplatUploadSceneResponse.self, from: data) else {
            throw NetworkError.decodingError
        }
        return decoded
    }

    func runSeaSplatJob(sceneId: String) async throws -> SeaSplatRunJobResponse {
        struct Body: Encodable { let scene_id: String }
        let b = Body(scene_id: sceneId)
        let base = Self.underwaterVisionModuleBaseURLString()
        guard let url = Self.underwaterVisionURL(
            base: base,
            directPath: "/v1/seasplat/jobs",
            proxiedPath: "/v1/seasplat/jobs"
        ) else { throw NetworkError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(b)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else {
            let parsed = Self.underwaterVisionErrorMessage(from: data) ?? Self.underwaterVisionErrorFallback(data: data)
            throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: parsed)
        }
        guard let decoded = try? JSONDecoder().decode(SeaSplatRunJobResponse.self, from: data) else {
            throw NetworkError.decodingError
        }
        return decoded
    }

    func seaSplatJobStatus(jobId: String) async throws -> SeaSplatJobStatusResponse {
        let base = Self.underwaterVisionModuleBaseURLString()
        guard let url = Self.underwaterVisionURL(
            base: base,
            directPath: "/v1/seasplat/jobs/\(jobId)",
            proxiedPath: "/v1/seasplat/jobs/\(jobId)"
        ) else { throw NetworkError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else {
            let parsed = Self.underwaterVisionErrorMessage(from: data) ?? Self.underwaterVisionErrorFallback(data: data)
            throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: parsed)
        }
        guard let decoded = try? JSONDecoder().decode(SeaSplatJobStatusResponse.self, from: data) else {
            throw NetworkError.decodingError
        }
        return decoded
    }

    func seaSplatRender(jobId: String) async throws -> Data {
        let base = Self.underwaterVisionModuleBaseURLString()
        guard let url = Self.underwaterVisionURL(
            base: base,
            directPath: "/v1/seasplat/jobs/\(jobId)/render",
            proxiedPath: "/v1/seasplat/jobs/\(jobId)/render"
        ) else { throw NetworkError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else {
            let parsed = Self.underwaterVisionErrorMessage(from: data) ?? Self.underwaterVisionErrorFallback(data: data)
            throw NetworkError.visionModuleHTTPError(statusCode: http.statusCode, message: parsed)
        }
        struct UVMEnvelope: Decodable { let image_jpeg_base64: String }
        let env = try JSONDecoder().decode(UVMEnvelope.self, from: data)
        guard let jpeg = Data(hexEncoded: env.image_jpeg_base64) else {
            throw NetworkError.decodingError
        }
        return jpeg
    }

    // MARK: - Trips API
    
    func getTrips(filters: TripViewModel.TripFilters? = nil) async throws -> [Trip] {
        var endpoint = "/api/trips"
        var queryItems: [URLQueryItem] = []
        
        if let filters = filters {
            if let tripType = filters.tripType {
                queryItems.append(URLQueryItem(name: "tripType", value: tripType.rawValue))
            }
            if let country = filters.country, !country.isEmpty {
                queryItems.append(URLQueryItem(name: "country", value: country))
            }
            if let startDate = filters.startDate {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withFullDate]
                queryItems.append(URLQueryItem(name: "startDate", value: formatter.string(from: startDate)))
            }
            if let endDate = filters.endDate {
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withFullDate]
                queryItems.append(URLQueryItem(name: "endDate", value: formatter.string(from: endDate)))
            }
            if let minCertificationLevel = filters.minCertificationLevel {
                queryItems.append(URLQueryItem(name: "minCertificationLevel", value: minCertificationLevel))
            }
            if let nitroxAvailable = filters.nitroxAvailable {
                queryItems.append(URLQueryItem(name: "nitroxAvailable", value: String(nitroxAvailable)))
            }
            if let equipmentRentalAvailable = filters.equipmentRentalAvailable {
                queryItems.append(URLQueryItem(name: "equipmentRentalAvailable", value: String(equipmentRentalAvailable)))
            }
            if let availableSpots = filters.availableSpots, availableSpots {
                queryItems.append(URLQueryItem(name: "availableSpots", value: "true"))
            }
        }
        
        if !queryItems.isEmpty {
            let queryString = queryItems.map { "\($0.name)=\($0.value ?? "")" }.joined(separator: "&")
            endpoint = "\(endpoint)?\(queryString)"
        }

        return try await request(endpoint: endpoint)
    }
    
    func getTrip(id: String) async throws -> Trip {
        let trip: Trip = try await request(endpoint: "/api/trips/\(id)")
        return trip
    }

    /// Импорт поездки по прямой ссылке сайта для текущего дайв-центра.
    func importTripFromWebsite(url: String, diveCenterId: String) async throws -> Trip {
        struct ImportReq: Codable {
            let url: String
            let diveCenterId: String
        }
        struct ImportRes: Codable {
            let tripId: String
        }
        let reqBody = ImportReq(url: url, diveCenterId: diveCenterId)
        let res: ImportRes = try await request(
            endpoint: "/api/trips/import/url",
            method: .post,
            body: reqBody
        )
        return try await getTrip(id: res.tripId)
    }
    
    func createTrip(_ trip: Trip, hotelName: String? = nil, hotelUrl: String? = nil, yachtName: String? = nil, yachtUrl: String? = nil) async throws -> Trip {
        // Create DTO for trip creation (exclude id, createdAt, updatedAt, organizerId - server sets it from auth token)
        struct CreateTripDTO: Codable {
            let organizerType: String
            let tripType: String
            let hotelId: String? // For daily trips - can be hotel name or ID
            let yachtId: String? // For safari trips - can be yacht name or ID
            let country: String
            let region: String?
            let startDate: Date
            let endDate: Date
            let minimumCertificationLevel: String?
            let minimumDives: Int?
            let description: String
            let photos: [String]
            let totalSpots: Int
            let availableCourses: [String]
            let nitroxAvailable: Bool
            let groupLeaderId: String?
            let program: [ProgramDayDTO]
            let additionalExpenses: [ExpenseDTO]
            let equipmentRentalAvailable: Bool
            let priceDetails: PriceDetailsDTO
            
            struct ProgramDayDTO: Codable {
                let date: Date
                let activities: [ActivityDTO]
                let description: String?
                
                struct ActivityDTO: Codable {
                    let time: String
                    let activity: String
                    let diveSiteId: String?
                    let diveCenterId: String?
                    let notes: String?
                }
            }
            
            struct ExpenseDTO: Codable {
                let expenseType: String
                let description: String
                let cost: Double
                let currency: String
            }
            
            struct PriceDetailsDTO: Codable {
                let roomPrices: [RoomPriceDTO]?
                let yachtPrices: [YachtPriceDTO]?
                let divingPrice: Double?
                let nonDivingPrice: Double?
                let currency: String
                
                struct RoomPriceDTO: Codable {
                    let roomType: String
                    // Note: roomCount is not sent to backend - backend doesn't expect it
                    let divingPrice: Double
                    let nonDivingPrice: Double
                }
                
                struct YachtPriceDTO: Codable {
                    let cabinType: String
                    // Note: cabinCount is not sent to backend - backend doesn't expect it
                    let divingPrice: Double
                    let nonDivingPrice: Double
                }
            }
        }
        
        // Convert Trip to DTO
        // For daily trips, create hotel first if name is provided, then use its ID
        // For safari trips, create yacht first if name is provided, then use its ID
        var finalHotelId: String? = nil
        var finalYachtId: String? = nil
        
        if trip.tripType == .daily {
            if let hotelName = hotelName, !hotelName.isEmpty {
                // Try to find existing hotel by name first
                let hotels = try? await getHotels()
                
                if let existingHotel = hotels?.first(where: { $0.name.lowercased() == hotelName.lowercased() }) {
                    finalHotelId = existingHotel.id
                } else {
                    // Create new hotel
                    do {
                        let newHotel = try await createHotel(name: hotelName, url: hotelUrl?.isEmpty == false ? hotelUrl : nil)
                        finalHotelId = newHotel.id
                    } catch {
                        throw error
                    }
                }
            }
        }
        
        if trip.tripType == .safari, let yachtName = yachtName, !yachtName.isEmpty {
            // Try to find existing yacht by name first
            let yachts = try? await getYachts()
            if let existingYacht = yachts?.first(where: { $0.name.lowercased() == yachtName.lowercased() }) {
                finalYachtId = existingYacht.id
            } else {
                // Create new yacht
                do {
                    let newYacht = try await createYacht(name: yachtName, url: yachtUrl?.isEmpty == false ? yachtUrl : nil)
                    finalYachtId = newYacht.id
                } catch {
                    throw error
                }
            }
        }// Validate groupLeaderId - if it exists, verify the instructor exists on backend
        // If not found, set to nil to avoid "Instructor not found" error
        var finalGroupLeaderId: String? = trip.groupLeaderId
        if let groupLeaderId = trip.groupLeaderId {
            // Try to verify instructor exists by fetching instructors for the dive center
            // If we can't verify or instructor doesn't exist, set to nil
            let instructors = try? await getDiveCenterInstructors(diveCenterId: trip.organizerId)
            if let instructors = instructors, !instructors.contains(where: { $0.id == groupLeaderId }) {
                // Instructor not found in the list - set to nil
                finalGroupLeaderId = nil
            }
        }
        
        let dto = CreateTripDTO(
            organizerType: trip.organizerType.rawValue,
            tripType: trip.tripType.rawValue,
            hotelId: finalHotelId,
            yachtId: finalYachtId,
            country: trip.country,
            region: trip.region, // Now sending region to backend
            startDate: trip.startDate,
            endDate: trip.endDate,
            minimumCertificationLevel: trip.minimumCertificationLevel,
            minimumDives: trip.minimumDives,
            description: trip.description,
            photos: trip.photos,
            totalSpots: trip.totalSpots,
            availableCourses: trip.availableCourses,
            nitroxAvailable: trip.nitroxAvailable,
            groupLeaderId: finalGroupLeaderId,
            program: trip.program.map { day in
                CreateTripDTO.ProgramDayDTO(
                    date: day.date,
                    activities: day.activities.map { activity in
                        CreateTripDTO.ProgramDayDTO.ActivityDTO(
                            time: activity.time,
                            activity: activity.activity,
                            diveSiteId: activity.diveSiteId,
                            diveCenterId: activity.diveCenterId,
                            notes: activity.notes
                        )
                    },
                    description: day.description
                )
            },
            additionalExpenses: trip.additionalExpenses.map { expense in
                CreateTripDTO.ExpenseDTO(
                    expenseType: expense.expenseType.rawValue,
                    description: expense.description,
                    cost: expense.cost,
                    currency: expense.currency
                )
            },
            equipmentRentalAvailable: trip.equipmentRentalAvailable,
            priceDetails: CreateTripDTO.PriceDetailsDTO(
                roomPrices: trip.priceDetails.roomPrices?.map { roomPrice in
                    CreateTripDTO.PriceDetailsDTO.RoomPriceDTO(
                        roomType: roomPrice.roomType,
                        // roomCount is not sent to backend
                        divingPrice: roomPrice.divingPrice,
                        nonDivingPrice: roomPrice.nonDivingPrice
                    )
                },
                yachtPrices: trip.priceDetails.yachtPrices?.map { yachtPrice in
                    CreateTripDTO.PriceDetailsDTO.YachtPriceDTO(
                        cabinType: yachtPrice.cabinType,
                        // cabinCount is not sent to backend
                        divingPrice: yachtPrice.divingPrice,
                        nonDivingPrice: yachtPrice.nonDivingPrice
                    )
                },
                divingPrice: trip.priceDetails.divingPrice,
                nonDivingPrice: trip.priceDetails.nonDivingPrice,
                currency: trip.priceDetails.currency
            )
        )
        let createdTrip: Trip = try await request(endpoint: "/api/trips", method: .post, body: dto)
        return createdTrip
    }
    
    func updateTrip(_ trip: Trip, hotelName: String? = nil, hotelUrl: String? = nil, yachtName: String? = nil, yachtUrl: String? = nil) async throws -> Trip {
        
        // Create UpdateTripDTO - similar to CreateTripDTO but excludes fields that server doesn't accept
        // IMPORTANT: We need to explicitly encode nil values as null for the server to clear fields
        struct UpdateTripDTO: Codable {
            let organizerType: String
            let tripType: String
            let hotelId: String?
            let yachtId: String?
            let country: String
            let region: String?
            let startDate: Date
            let endDate: Date
            let minimumCertificationLevel: String?
            let minimumDives: Int?
            let description: String
            let photos: [String]
            let totalSpots: Int
            let availableCourses: [String]
            let nitroxAvailable: Bool
            let groupLeaderId: String?
            let program: [ProgramDayDTO] // Note: server expects "program", not "programDays"
            let additionalExpenses: [ExpenseDTO]
            let equipmentRentalAvailable: Bool
            let priceDetails: PriceDetailsDTO
            
            struct ProgramDayDTO: Codable {
                let date: Date
                let activities: [ActivityDTO]
                let description: String?
                
                struct ActivityDTO: Codable {
                    let time: String
                    let activity: String
                    let diveSiteId: String?
                    let diveCenterId: String?
                    let notes: String?
                }
            }
            
            struct ExpenseDTO: Codable {
                let expenseType: String
                let description: String
                let cost: Double
                let currency: String
            }
            
            struct PriceDetailsDTO: Codable {
                let roomPrices: [RoomPriceDTO]?
                let yachtPrices: [YachtPriceDTO]?
                let divingPrice: Double?
                let nonDivingPrice: Double?
                let currency: String
                
                struct RoomPriceDTO: Codable {
                    let roomType: String
                    // Note: roomCount is not sent to backend - backend doesn't expect it
                    let divingPrice: Double
                    let nonDivingPrice: Double
                }
                
                struct YachtPriceDTO: Codable {
                    let cabinType: String
                    // Note: cabinCount is not sent to backend - backend doesn't expect it
                    let divingPrice: Double
                    let nonDivingPrice: Double
                }
            }
        }
        
        // Convert Trip to DTO
        // For daily trips, create/update hotel first if name is provided, then use its ID
        // For safari trips, create/update yacht first if name is provided, then use its ID
        // IMPORTANT: When tripType changes, we must clear the opposite type's ID
        var finalHotelId: String? = nil
        var finalYachtId: String? = nil
        
        if trip.tripType == .daily {
            // For daily trips, we need hotelId and must clear yachtId
            finalYachtId = nil // Explicitly clear yachtId for daily trips
            
            if let hotelName = hotelName, !hotelName.isEmpty {
                // Try to find existing hotel by name first
                let hotels = try? await getHotels()
                
                // First, check if the trip already has a hotel
                // Note: trip.hotelId might be a name (for backward compatibility) or an ID
                if let existingTripHotelId = trip.hotelId {
                    // Try to find hotel by ID first
                    if let existingTripHotel = hotels?.first(where: { $0.id == existingTripHotelId }) {
                        // Found by ID - check if name matches
                        if existingTripHotel.name.lowercased() == hotelName.lowercased() {
                            finalHotelId = existingTripHotelId
                        } else {
                            // Name changed - try to find by new name or create new
                            if let existingHotel = hotels?.first(where: { $0.name.lowercased() == hotelName.lowercased() }) {
                                finalHotelId = existingHotel.id
                            } else {
                                // Create new hotel with new name
                                do {
                                    let newHotel = try await createHotel(name: hotelName, url: hotelUrl?.isEmpty == false ? hotelUrl : nil)
                                    finalHotelId = newHotel.id
                                } catch {
                                    throw error
                                }
                            }
                        }
                    } else if existingTripHotelId.lowercased() == hotelName.lowercased() {
                        // trip.hotelId is a name (not an ID) and it matches - try to find by name or create
                        if let existingHotel = hotels?.first(where: { $0.name.lowercased() == hotelName.lowercased() }) {
                            finalHotelId = existingHotel.id
                        } else {
                            // Create new hotel
                            do {
                                let newHotel = try await createHotel(name: hotelName, url: hotelUrl?.isEmpty == false ? hotelUrl : nil)
                                finalHotelId = newHotel.id
                            } catch {
                                throw error
                            }
                        }
                    } else {
                        // trip.hotelId is a name but it changed - try to find by new name or create
                        if let existingHotel = hotels?.first(where: { $0.name.lowercased() == hotelName.lowercased() }) {
                            finalHotelId = existingHotel.id
                        } else {
                            // Create new hotel with new name
                            do {
                                let newHotel = try await createHotel(name: hotelName, url: hotelUrl?.isEmpty == false ? hotelUrl : nil)
                                finalHotelId = newHotel.id
                            } catch {
                                throw error
                            }
                        }
                    }
                } else if let existingHotel = hotels?.first(where: { $0.name.lowercased() == hotelName.lowercased() }) {
                    // No existing hotel linked, but found one by name
                    finalHotelId = existingHotel.id
                } else {
                    // Create new hotel
                    do {
                        let newHotel = try await createHotel(name: hotelName, url: hotelUrl?.isEmpty == false ? hotelUrl : nil)
                        finalHotelId = newHotel.id
                    } catch {
                        throw error
                    }
                }
            } else {
                // Use existing hotelId from trip if no new name provided
                finalHotelId = trip.hotelId
            }
        } else if trip.tripType == .safari {
            // For safari trips, we need yachtId and must clear hotelId
            finalHotelId = nil // Explicitly clear hotelId for safari trips
            
            if let yachtName = yachtName, !yachtName.isEmpty {
                // Try to find existing yacht by name first
                // IMPORTANT: Load yachts fresh each time to ensure we have the latest data
                let yachts = try? await getYachts()
                
                // First, check if the trip already has a yacht
                // Note: trip.yachtId might be a name (for backward compatibility) or an ID
                if let existingTripYachtId = trip.yachtId {
                    // Try to find yacht by ID first
                    if let existingTripYacht = yachts?.first(where: { $0.id == existingTripYachtId }) {
                        // Found by ID - check if name matches
                        if existingTripYacht.name.lowercased() == yachtName.lowercased() {
                            finalYachtId = existingTripYachtId
                        } else {
                            // Name changed - try to find by new name or create new
                            if let existingYacht = yachts?.first(where: { $0.name.lowercased() == yachtName.lowercased() }) {
                                finalYachtId = existingYacht.id
                            } else {
                                // Create new yacht with new name
                                do {
                                    let newYacht = try await createYacht(name: yachtName, url: yachtUrl?.isEmpty == false ? yachtUrl : nil)
                                    finalYachtId = newYacht.id
                                } catch {
                                    throw error
                                }
                            }
                        }
                    } else if existingTripYachtId.lowercased() == yachtName.lowercased() {
                        // trip.yachtId is a name (not an ID) and it matches - try to find by name or create
                        // IMPORTANT: Reload yachts after potential creation to ensure we have the latest data
                        var updatedYachts = yachts
                        if let existingYacht = updatedYachts?.first(where: { $0.name.lowercased() == yachtName.lowercased() }) {
                            finalYachtId = existingYacht.id
                        } else {
                            // Create new yacht, then reload and use it
                            do {
                                let newYacht = try await createYacht(name: yachtName, url: yachtUrl?.isEmpty == false ? yachtUrl : nil)
                                // Reload yachts to get the newly created one
                                updatedYachts = try? await getYachts()
                                if let foundYacht = updatedYachts?.first(where: { $0.id == newYacht.id }) {
                                    finalYachtId = foundYacht.id
                                } else {
                                    finalYachtId = newYacht.id
                                }
                            } catch {
                                throw error
                            }
                        }
                    } else {
                        // trip.yachtId is a name but it changed - try to find by new name or create
                        if let existingYacht = yachts?.first(where: { $0.name.lowercased() == yachtName.lowercased() }) {
                            finalYachtId = existingYacht.id
                        } else {
                            // Create new yacht with new name
                            do {
                                let newYacht = try await createYacht(name: yachtName, url: yachtUrl?.isEmpty == false ? yachtUrl : nil)
                                finalYachtId = newYacht.id
                            } catch {
                                throw error
                            }
                        }
                    }
                } else if let existingYacht = yachts?.first(where: { $0.name.lowercased() == yachtName.lowercased() }) {
                    // No existing yacht linked, but found one by name
                    finalYachtId = existingYacht.id
                } else {
                    // Create new yacht
                    do {
                        let newYacht = try await createYacht(name: yachtName, url: yachtUrl?.isEmpty == false ? yachtUrl : nil)
                        finalYachtId = newYacht.id
                    } catch {
                        throw error
                    }
                }
            } else {
                // Use existing yachtId from trip if no new name provided
                finalYachtId = trip.yachtId
            }
        }// Validate groupLeaderId - if it exists, verify the instructor exists on backend
        // If not found, set to nil to avoid "Instructor not found" error
        var finalGroupLeaderId: String? = trip.groupLeaderId
        if let groupLeaderId = trip.groupLeaderId {
            // Try to verify instructor exists by fetching instructors for the dive center
            // If we can't verify or instructor doesn't exist, set to nil
            let instructors = try? await getDiveCenterInstructors(diveCenterId: trip.organizerId)
            if let instructors = instructors, !instructors.contains(where: { $0.id == groupLeaderId }) {
                // Instructor not found in the list - set to nil
                finalGroupLeaderId = nil
            }
        }
        
        let dto = UpdateTripDTO(
            organizerType: trip.organizerType.rawValue,
            tripType: trip.tripType.rawValue,
            hotelId: finalHotelId,
            yachtId: finalYachtId,
            country: trip.country,
            region: trip.region, // Now sending region to backend
            startDate: trip.startDate,
            endDate: trip.endDate,
            minimumCertificationLevel: trip.minimumCertificationLevel,
            minimumDives: trip.minimumDives,
            description: trip.description,
            photos: trip.photos,
            totalSpots: trip.totalSpots,
            availableCourses: trip.availableCourses,
            nitroxAvailable: trip.nitroxAvailable,
            groupLeaderId: finalGroupLeaderId,
            program: trip.program.map { day in
                UpdateTripDTO.ProgramDayDTO(
                    date: day.date,
                    activities: day.activities.map { activity in
                        UpdateTripDTO.ProgramDayDTO.ActivityDTO(
                            time: activity.time,
                            activity: activity.activity,
                            diveSiteId: activity.diveSiteId,
                            diveCenterId: activity.diveCenterId,
                            notes: activity.notes
                        )
                    },
                    description: day.description
                )
            },
            additionalExpenses: trip.additionalExpenses.map { expense in
                UpdateTripDTO.ExpenseDTO(
                    expenseType: expense.expenseType.rawValue,
                    description: expense.description,
                    cost: expense.cost,
                    currency: expense.currency
                )
            },
            equipmentRentalAvailable: trip.equipmentRentalAvailable,
            priceDetails: UpdateTripDTO.PriceDetailsDTO(
                roomPrices: trip.priceDetails.roomPrices?.map { roomPrice in
                    UpdateTripDTO.PriceDetailsDTO.RoomPriceDTO(
                        roomType: roomPrice.roomType,
                        // roomCount is not sent to backend
                        divingPrice: roomPrice.divingPrice,
                        nonDivingPrice: roomPrice.nonDivingPrice
                    )
                },
                yachtPrices: trip.priceDetails.yachtPrices?.map { yachtPrice in
                    UpdateTripDTO.PriceDetailsDTO.YachtPriceDTO(
                        cabinType: yachtPrice.cabinType,
                        // cabinCount is not sent to backend
                        divingPrice: yachtPrice.divingPrice,
                        nonDivingPrice: yachtPrice.nonDivingPrice
                    )
                },
                divingPrice: trip.priceDetails.divingPrice,
                nonDivingPrice: trip.priceDetails.nonDivingPrice,
                currency: trip.priceDetails.currency
            )
        )
        // Try PUT first for full updates (especially when tripType changes)
        // PATCH may not support changing tripType, so we use PUT for complete replacement
        do {
            let result: Trip = try await request(endpoint: "/api/trips/\(trip.id)", method: .put, body: dto)
            return result
        } catch let putError as NetworkError {
            // If PUT fails with 404, try PATCH (but let 401 pass through for token refresh)
            if case .serverError(404) = putError {
                let result: Trip = try await request(endpoint: "/api/trips/\(trip.id)", method: .patch, body: dto)
                return result
            }
            throw putError
        }
    }
    
    func deleteTrip(tripId: String) async throws {
        let _: EmptyResponse = try await request(endpoint: "/api/trips/\(tripId)", method: .delete)
    }
    
    func bookTrip(tripId: String, participants: [Trip.TripParticipant]) async throws -> Trip {
        struct BookTripRequest: Codable {
            let participants: [Trip.TripParticipant]
        }
        let request = BookTripRequest(participants: participants)
        return try await self.request(endpoint: "/api/trips/\(tripId)/book", method: .post, body: request)
    }
    
    // MARK: - Hotels API
    
    func getHotels() async throws -> [Hotel] {
        return try await request(endpoint: "/api/hotels")
    }
    
    func getHotel(id: String) async throws -> Hotel {
        return try await request(endpoint: "/api/hotels/\(id)")
    }
    
    func createHotel(name: String, url: String? = nil) async throws -> Hotel {
        struct CreateHotelDTO: Codable {
            let name: String
            let description: String
            let address: String
            let city: String
            let country: String
            let photos: [String]
            let amenities: [String]
            let roomTypes: [RoomTypeDTO]
            
            struct RoomTypeDTO: Codable {
                let name: String
                let description: String
                let maxOccupancy: Int
            }
        }
        
        // Create hotel with minimal required fields
        let dto = CreateHotelDTO(
            name: name,
            description: name, // Use name as description if not provided
            address: name, // Use name as address if not provided
            city: "Unknown",
            country: "Unknown",
            photos: [],
            amenities: [],
            roomTypes: []
        )
        
        return try await request(endpoint: "/api/hotels", method: .post, body: dto)
    }
    
    func createYacht(name: String, url: String? = nil) async throws -> Yacht {
        struct CreateYachtDTO: Codable {
            let name: String
            let description: String
            let photos: [String]
            let capacity: Int
            let amenities: [String]
            let cabinTypes: [CabinTypeDTO]
            
            struct CabinTypeDTO: Codable {
                let name: String
                let description: String
                let capacity: Int
            }
        }
        
        // Create yacht with minimal required fields
        let dto = CreateYachtDTO(
            name: name,
            description: name, // Use name as description if not provided
            photos: [],
            capacity: 0, // Default capacity
            amenities: [],
            cabinTypes: []
        )
        
        return try await request(endpoint: "/api/yachts", method: .post, body: dto)
    }
    
    // MARK: - Yachts API
    
    func getYachts() async throws -> [Yacht] {
        return try await request(endpoint: "/api/yachts")
    }
    
    func getYacht(id: String) async throws -> Yacht {
        return try await request(endpoint: "/api/yachts/\(id)")
    }
    
    // MARK: - Courses API
    
    func getCourses(diveCenterId: String? = nil) async throws -> [Course] {
        var endpoint = "/api/courses"
        if let diveCenterId = diveCenterId {
            endpoint += "?diveCenterId=\(diveCenterId)"
        }

        return try await request(endpoint: endpoint)
    }
    
    func getCourse(id: String) async throws -> Course {
        return try await request(endpoint: "/api/courses/\(id)")
    }
    
    func createCourse(_ course: Course) async throws -> Course {
        // Create DTO for course creation (without id, createdAt, updatedAt, and module ids)
        struct CreateCourseDTO: Codable {
            let name: String
            let level: String
            let description: String
            let trainingSystems: [String]
            let program: [CreateModuleDTO]
            let duration: Int
            let prerequisites: [String]?
            let diveCenterId: String?
            let instructorId: String?
            
            struct CreateModuleDTO: Codable {
                let title: String
                let description: String
                let duration: Int
                let moduleType: String
                let order: Int
            }
        }
        
        // Convert Course to DTO
        let modules = course.program
            .filter { !$0.description.isEmpty }
            .map { module in
                CreateCourseDTO.CreateModuleDTO(
                    title: module.title,
                    description: module.description,
                    duration: module.duration,
                    moduleType: module.moduleType.rawValue,
                    order: module.order
                )
            }
        
        let dto = CreateCourseDTO(
            name: course.name,
            level: course.level.rawValue,
            description: course.description,
            trainingSystems: course.trainingSystems,
            program: modules,
            duration: course.duration,
            prerequisites: course.prerequisites,
            diveCenterId: course.diveCenterId,
            instructorId: course.instructorId
        )
        
        return try await request(endpoint: "/api/courses", method: .post, body: dto)
    }
    
    func updateCourse(_ course: Course) async throws -> Course {
        // Create DTO for course update (similar to creation, but may need id)
        struct UpdateCourseDTO: Codable {
            let name: String
            let level: String
            let description: String
            let trainingSystems: [String]
            let program: [UpdateModuleDTO]
            let duration: Int
            let prerequisites: [String]?
            let diveCenterId: String?
            let instructorId: String?
            
            struct UpdateModuleDTO: Codable {
                let title: String
                let description: String
                let duration: Int
                let moduleType: String
                let order: Int
            }
        }
        
        // Convert Course to DTO - filter out modules with empty descriptions
        let cleanedModules = course.program
            .filter { !$0.description.isEmpty }
            .map { module in
                UpdateCourseDTO.UpdateModuleDTO(
                    title: module.title,
                    description: module.description,
                    duration: module.duration,
                    moduleType: module.moduleType.rawValue,
                    order: module.order
                )
            }
        
        let dto = UpdateCourseDTO(
            name: course.name,
            level: course.level.rawValue,
            description: course.description,
            trainingSystems: course.trainingSystems,
            program: cleanedModules,
            duration: course.duration,
            prerequisites: course.prerequisites,
            diveCenterId: course.diveCenterId,
            instructorId: course.instructorId
        )
        
            let endpoint = "/api/courses/\(course.id)"
            // Try PATCH first, as some backends prefer PATCH for updates
        do {
            // Try PATCH first - let the request method handle 401 token refresh automatically
            return try await request(endpoint: endpoint, method: .patch, body: dto)
            } catch let patchError as NetworkError {
            // If PATCH fails with 404, try PUT (but let 401 pass through for token refresh)
                if case .serverError(404) = patchError {
                // Try PUT - let the request method handle 401 token refresh automatically
                return try await request(endpoint: endpoint, method: .put, body: dto)
                } else {
                // For other errors (including 401, 400), rethrow to let caller handle
                // The request method should have already tried token refresh for 401
                    throw patchError
                }
        }
    }
    
    func deleteCourse(courseId: String) async throws {
        let _: EmptyResponse = try await request(endpoint: "/api/courses/\(courseId)", method: .delete)
    }
    
    // MARK: - Dive Center Instructors API
    
    func getDiveCenterInstructors(diveCenterId: String) async throws -> [Instructor] {
        let endpoint = "/api/v1/dive-centers/\(diveCenterId)/instructors"
        return try await request(endpoint: endpoint)
    }

    // MARK: - Partner registration (public, no auth required)

    func submitPartnerRegistration(body: PartnerRegistrationRequestBody) async throws -> PartnerRegistrationAPIResponse {
        try await request(
            endpoint: "/api/v1/partner-registrations",
            method: .post,
            body: body
        )
    }

    func addInstructorToDiveCenter(userId: String, diveCenterId: String) async throws -> Instructor {
        struct AddInstructorBody: Codable {
            let userId: String
        }
        return try await self.request(
            endpoint: "/api/v1/dive-centers/\(diveCenterId)/instructors",
            method: .post,
            body: AddInstructorBody(userId: userId)
        )
    }
    
    func removeInstructorFromDiveCenter(instructorId: String, diveCenterId: String) async throws {
        let _: EmptyResponse = try await request(
            endpoint: "/api/v1/dive-centers/\(diveCenterId)/instructors/\(instructorId)",
            method: .delete
        )
    }

    /// Карточка инструктора: описание (bio) — правит админ центра.
    func patchInstructorProfile(diveCenterId: String, userId: String, bio: String?) async throws -> User {
        /// Явно кодируем `bio: null`, иначе JSONEncoder опускает ключ и Nest не очищает поле.
        struct PatchInstructorBody: Encodable {
            let bio: String?
            enum CodingKeys: String, CodingKey {
                case bio
            }
            func encode(to encoder: Encoder) throws {
                var c = encoder.container(keyedBy: CodingKeys.self)
                if let bio {
                    try c.encode(bio, forKey: .bio)
                } else {
                    try c.encodeNil(forKey: .bio)
                }
            }
        }
        struct PatchOk: Codable {
            let ok: Bool
        }
        // #region agent log
        Self.agentDebugLog(
            "NetworkService.swift:patchInstructorProfile",
            "before_patch",
            hypothesisId: "H2",
            data: ["diveCenterId": diveCenterId, "userId": userId]
        )
        // #endregion
        do {
            let _: PatchOk = try await request(
                endpoint: "/api/v1/dive-centers/\(diveCenterId)/instructors/\(userId)",
                method: .patch,
                body: PatchInstructorBody(bio: bio)
            )
            // #region agent log
            Self.agentDebugLog(
                "NetworkService.swift:patchInstructorProfile",
                "patch_decode_ok",
                hypothesisId: "H3",
                data: [:]
            )
            // #endregion
            let user = try await getUser(userId: userId)
            // #region agent log
            Self.agentDebugLog(
                "NetworkService.swift:patchInstructorProfile",
                "getUser_ok",
                hypothesisId: "H4",
                data: ["userId": user.id]
            )
            // #endregion
            return user
        } catch {
            // #region agent log
            Self.agentDebugLog(
                "NetworkService.swift:patchInstructorProfile",
                "error",
                hypothesisId: "H2",
                data: ["err": String(describing: error)]
            )
            // #endregion
            throw error
        }
    }
    
    // MARK: - Test Data Initialization API
    
    /// Initializes test data on the backend (trips, instructors, courses, etc.)
    /// This should be called once to seed the database with test data
    func initializeTestData() async throws {
        struct InitializeTestDataResponse: Codable {
            let message: String
            let tripsCreated: Int?
            let instructorsCreated: Int?
            let coursesCreated: Int?
        }
        let _: InitializeTestDataResponse = try await request(
            endpoint: "/api/test/initialize",
            method: .post
        )
    }

    // MARK: - Image Processing Service (async jobs, v1)

    struct ImageUploadResponse: Codable {
        let image_id: String
    }

    struct ImageProcessJobCreateResponse: Codable {
        let job_id: String
        let status: String
    }

    struct ImageProcessStatusResponse: Codable {
        let job_id: String
        let status: String
        let progress: Int
        /// Текст ошибки от Nest/Python, если status == "failed"
        let error: String?
    }

    struct ImageProcessParamsPayload: Encodable {
        let depth: Double
        let strength: Double
        let dehaze: Double
        let clarity: Double
        let temperature: Double
        let auto_ai: Bool
        /// `default` (Lee Symmetry classical) or `jmse1820` (Li et al. JMSE 2025).
        let pipeline: String

        init(
            depth: Double,
            strength: Double,
            dehaze: Double,
            clarity: Double,
            temperature: Double,
            auto_ai: Bool,
            pipeline: String = "default"
        ) {
            self.depth = depth
            self.strength = strength
            self.dehaze = dehaze
            self.clarity = clarity
            self.temperature = temperature
            self.auto_ai = auto_ai
            self.pipeline = pipeline
        }
    }

    private struct ImageProcessRequestBody: Encodable {
        let image_id: String
        /// Дублирует `params.pipeline`: Nest читает корневое поле, если вложенный объект теряет ключ.
        let pipeline: String
        let params: ImageProcessParamsPayload
    }

    /// Multipart upload → `image_id` for `createImageProcessJob`.
    func uploadImageForProcessing(jpegData: Data, filename: String = "photo.jpg") async throws -> String {
        let path = "/api/v1/image/upload"
        guard let url = URL(string: baseURL + path) else { throw NetworkError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(jpegData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else { throw NetworkError.serverError(http.statusCode) }
        let decoded = try JSONDecoder().decode(ImageUploadResponse.self, from: data)
        return decoded.image_id
    }

    func createImageProcessJob(imageId: String, params: ImageProcessParamsPayload) async throws -> ImageProcessJobCreateResponse {
        let body = ImageProcessRequestBody(image_id: imageId, pipeline: params.pipeline, params: params)
        let res: ImageProcessJobCreateResponse = try await request(
            endpoint: "/api/v1/image/process",
            method: .post,
            body: body
        )
        return res
    }

    func getImageProcessStatus(jobId: String) async throws -> ImageProcessStatusResponse {
        try await request(
            endpoint: "/api/v1/image/status/\(jobId)",
            method: .get
        )
    }

    /// Raw JPEG/PNG bytes when job is done.
    func downloadImageProcessResult(jobId: String) async throws -> Data {
        let path = "/api/v1/image/result/\(jobId)"
        guard let url = URL(string: baseURL + path) else { throw NetworkError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if let token = getAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw NetworkError.noData }
        guard (200...299).contains(http.statusCode) else { throw NetworkError.serverError(http.statusCode) }
        return data
    }

    /// Poll until `done` or `failed`, then returns image data (downloads result).
    func waitForImageProcessJob(jobId: String, pollIntervalMs: UInt64 = 400, maxWaitSeconds: TimeInterval = 120) async throws -> Data {
        let deadline = Date().addingTimeInterval(maxWaitSeconds)
        while Date() < deadline {
            let st = try await getImageProcessStatus(jobId: jobId)
            switch st.status {
            case "done":
                return try await downloadImageProcessResult(jobId: jobId)
            case "failed":
                let msg = (st.error?.isEmpty == false) ? (st.error ?? "") : "Image job failed on server (check Nest logs and ai-service)."
                throw NetworkError.unknown(
                    NSError(domain: "ImageProcessing", code: 500, userInfo: [NSLocalizedDescriptionKey: msg])
                )
            default:
                try await Task.sleep(nanoseconds: pollIntervalMs * 1_000_000)
            }
        }
        throw NetworkError.unknown(
            NSError(
                domain: "ImageProcessing",
                code: -1001,
                userInfo: [NSLocalizedDescriptionKey: "Processing timeout"]
            )
        )
    }
}

// MARK: - Hex JPEG from underwater-vision-module JSON

private extension Data {
    init?(hexEncoded string: String) {
        let hex = string.filter { !$0.isWhitespace }
        guard hex.count.isMultiple(of: 2), !hex.isEmpty else { return nil }
        var data = Data(capacity: hex.count / 2)
        var i = hex.startIndex
        while i < hex.endIndex {
            let j = hex.index(i, offsetBy: 2)
            guard let byte = UInt8(hex[i..<j], radix: 16) else { return nil }
            data.append(byte)
            i = j
        }
        self = data
    }
}
