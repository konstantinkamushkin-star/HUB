//
//  DiveEditorAnalyticsService.swift
//  DiveHub
//

import Foundation
import UIKit

enum DiveEditorAnalyticsEvent: String {
    case underwaterTabOpened = "underwater_tab_opened"
    case photoSelected = "photo_selected"
    case autoAiStarted = "auto_ai_started"
    case autoAiCompleted = "auto_ai_completed"
    case manualSliderChanged = "manual_slider_changed"
    case savePressed = "save_pressed"
    case sharePressed = "share_pressed"
    case processingFailed = "processing_failed"
}

final class DiveEditorAnalyticsService {
    static let shared = DiveEditorAnalyticsService()

    private let queue = DispatchQueue(label: "dive.editor.analytics", qos: .utility)
    private let bufferKey = "dive_editor_analytics_buffer"
    private let maxBuffered = 200

    private init() {}

    func track(
        _ event: DiveEditorAnalyticsEvent,
        fileType: String? = nil,
        processingMode: String? = nil,
        success: Bool? = nil,
        durationMs: Int? = nil,
        extra: [String: String] = [:]
    ) {
        let userId = AuthenticationService.shared.currentUser?.id ?? "anonymous"
        let device = UIDevice.current.model
        let ts = ISO8601DateFormatter().string(from: Date())

        var payload: [String: Any] = [
            "event": event.rawValue,
            "user_id": userId,
            "timestamp": ts,
            "device_type": device
        ]
        if let fileType { payload["file_type"] = fileType }
        if let processingMode { payload["processing_mode"] = processingMode }
        if let success { payload["success"] = success }
        if let durationMs { payload["duration_ms"] = durationMs }
        for (k, v) in extra { payload[k] = v }

        queue.async { self.appendToBuffer(payload) }

        #if DEBUG
        print("📊 [DiveEditorAnalytics] \(event.rawValue) \(payload)")
        #endif
    }

    private func appendToBuffer(_ payload: [String: Any]) {
        var arr = (UserDefaults.standard.array(forKey: bufferKey) as? [[String: Any]]) ?? []
        arr.append(payload)
        if arr.count > maxBuffered {
            arr.removeFirst(arr.count - maxBuffered)
        }
        UserDefaults.standard.set(arr, forKey: bufferKey)
    }
}
