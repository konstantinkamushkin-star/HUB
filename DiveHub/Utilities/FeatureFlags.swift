//
//  FeatureFlags.swift
//  DiveHub
//

import Foundation

enum FeatureFlags {
    /// UserDefaults / `@AppStorage` key for Dive Editor visibility.
    static let underwaterEditorKey = "underwater_editor_enabled"

    /// When false: hide Dive Editor tab and in-context edit actions.
    static var underwaterEditorEnabled: Bool {
        get {
            if UserDefaults.standard.object(forKey: underwaterEditorKey) == nil {
                return true
            }
            return UserDefaults.standard.bool(forKey: underwaterEditorKey)
        }
        set {
            UserDefaults.standard.set(newValue, forKey: underwaterEditorKey)
        }
    }
}
