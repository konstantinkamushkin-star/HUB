//
//  KeychainService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import Security

class KeychainService {
    static let shared = KeychainService()
    
    private let service = "com.divehub.app"
    
    private init() {}
    
    // MARK: - Token Storage
    
    func saveAccessToken(_ token: String) -> Bool {
        return save(token, forKey: "access_token")
    }
    
    func getAccessToken() -> String? {
        return get(forKey: "access_token")
    }
    
    func saveRefreshToken(_ token: String) -> Bool {
        return save(token, forKey: "refresh_token")
    }
    
    func getRefreshToken() -> String? {
        return get(forKey: "refresh_token")
    }
    
    func deleteAccessToken() -> Bool {
        return delete(forKey: "access_token")
    }
    
    func deleteRefreshToken() -> Bool {
        return delete(forKey: "refresh_token")
    }
    
    func clearAllTokens() {
        _ = deleteAccessToken()
        _ = deleteRefreshToken()
    }
    
    // MARK: - Generic Keychain Operations
    
    private func save(_ value: String, forKey key: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]
        
        // Delete existing item first
        SecItemDelete(query as CFDictionary)
        
        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }
    
    private func get(forKey key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess,
              let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else {
            return nil
        }
        
        return value
    }
    
    private func delete(forKey key: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
