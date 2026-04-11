//
//  ShopService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

class ShopService {
    static let shared = ShopService()
    
    private init() {}
    
    /// Проверяет, является ли пользователь владельцем магазина
    func isShopOwner(userId: String) async -> Bool {
        do {
            struct ShopsResponse: Codable {
                let success: Bool
                let data: [Shop]
            }
            
            let response: ShopsResponse = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops",
                method: .get
            )
            
            // Проверяем, есть ли магазин, где ownerId совпадает с userId
            return response.data.contains { $0.ownerId == userId }
        } catch {
            print("Error checking shop ownership: \(error)")
            return false
        }
    }
    
    /// Получает магазин пользователя
    func getUserShop(userId: String) async -> Shop? {
        do {
            struct ShopsResponse: Codable {
                let success: Bool
                let data: [Shop]
            }
            
            let response: ShopsResponse = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops",
                method: .get
            )
            
            return response.data.first { $0.ownerId == userId }
        } catch {
            print("Error getting user shop: \(error)")
            return nil
        }
    }
}
