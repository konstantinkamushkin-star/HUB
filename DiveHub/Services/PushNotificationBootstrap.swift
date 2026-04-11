//
//  PushNotificationBootstrap.swift
//  DiveHub
//

import UserNotifications
import UIKit

enum PushNotificationBootstrap {
    /// Call after login; registers with APNs and sends token to `POST /api/users/me/push-token`.
    static func requestAndRegister() {
        guard AuthenticationService.shared.isAuthenticated else { return }
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .denied:
                return
            case .notDetermined:
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { ok, _ in
                    if ok {
                        DispatchQueue.main.async {
                            UIApplication.shared.registerForRemoteNotifications()
                        }
                    }
                }
            default:
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }
}
