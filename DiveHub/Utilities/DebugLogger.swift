//
//  DebugLogger.swift
//  DiveHub
//
//  Created for debugging purposes
//

import Foundation

struct DebugLogger {
    nonisolated private static let logPath = "/Users/admin/Desktop/appp/DivePROD/.cursor/debug.log"
    
    nonisolated static func log(location: String, message: String, data: [String: Any], hypothesisId: String, runId: String = "run1") {
        let logData: [String: Any] = [
            "location": location,
            "message": message,
            "data": data,
            "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            "sessionId": "debug-session",
            "runId": runId,
            "hypothesisId": hypothesisId
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: logData),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return
        }
        
        let logLine = jsonString + "\n"
        guard let logDataToWrite = logLine.data(using: .utf8) else {
            return
        }
        
        if let fileHandle = FileHandle(forWritingAtPath: logPath) {
            fileHandle.seekToEndOfFile()
            fileHandle.write(logDataToWrite)
            fileHandle.closeFile()
        } else {
            // File doesn't exist, create it
            try? logDataToWrite.write(to: URL(fileURLWithPath: logPath), options: .atomic)
        }
    }
}
