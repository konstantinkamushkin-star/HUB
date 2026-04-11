//
//  TranslationService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

/// Service for automatic translation of text content
class TranslationService {
    static let shared = TranslationService()
    
    private let cache = NSCache<NSString, NSString>()
    private let maxCacheSize = 1000
    
    private init() {
        cache.countLimit = maxCacheSize
    }
    
    /// Translates text from source language to target language
    /// - Parameters:
    ///   - text: Text to translate
    ///   - sourceLanguage: Source language code (e.g., "en", "ru")
    ///   - targetLanguage: Target language code (e.g., "en", "ru")
    /// - Returns: Translated text
    func translate(
        text: String,
        from sourceLanguage: String,
        to targetLanguage: String
    ) async throws -> String {
        // If source and target are the same, return original text
        if sourceLanguage == targetLanguage {
            return text
        }
        
        // Check cache first
        let cacheKey = "\(sourceLanguage)-\(targetLanguage):\(text)" as NSString
        if let cached = cache.object(forKey: cacheKey) {
            return cached as String
        }
        
        // Try to translate via backend API first
        do {
            let translated = try await NetworkService.shared.translateText(
                text: text,
                from: sourceLanguage,
                to: targetLanguage
            )
            // Cache the result
            cache.setObject(translated as NSString, forKey: cacheKey)
            return translated
        } catch {
            // If backend translation fails, try local translation using system APIs
            // For now, we'll return the original text if translation fails
            // In production, you might want to use a local translation library
            print("⚠️ Translation failed: \(error.localizedDescription)")
            return text
        }
    }
    
    /// Translates text to user's current language
    /// - Parameters:
    ///   - text: Text to translate
    ///   - sourceLanguage: Source language code (defaults to "en")
    /// - Returns: Translated text
    func translateToUserLanguage(
        text: String,
        from sourceLanguage: String = "en"
    ) async -> String {
        let targetLanguage = LocalizationService.shared.currentLanguage.rawValue
        
        // If already in target language, return as is
        if sourceLanguage == targetLanguage {
            return text
        }
        
        do {
            return try await translate(
                text: text,
                from: sourceLanguage,
                to: targetLanguage
            )
        } catch {
            // Return original text if translation fails
            return text
        }
    }
    
    /// Batch translates multiple texts
    /// - Parameters:
    ///   - texts: Array of texts to translate
    ///   - sourceLanguage: Source language code
    ///   - targetLanguage: Target language code
    /// - Returns: Array of translated texts
    func translateBatch(
        texts: [String],
        from sourceLanguage: String,
        to targetLanguage: String
    ) async throws -> [String] {
        // If source and target are the same, return original texts
        if sourceLanguage == targetLanguage {
            return texts
        }
        
        // Try batch translation via backend
        do {
            return try await NetworkService.shared.translateTextBatch(
                texts: texts,
                from: sourceLanguage,
                to: targetLanguage
            )
        } catch {
            // Fallback to individual translations
            var results: [String] = []
            for text in texts {
                do {
                    let translated = try await translate(
                        text: text,
                        from: sourceLanguage,
                        to: targetLanguage
                    )
                    results.append(translated)
                } catch {
                    results.append(text) // Use original if translation fails
                }
            }
            return results
        }
    }
    
    /// Clears the translation cache
    func clearCache() {
        cache.removeAllObjects()
    }
}
