//
//  OCRService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import UIKit
import Vision

struct CertificateData {
    var organization: String?
    var level: String?
    var issueDate: Date?
    var instructorNumber: String?
}

class OCRService {
    static let shared = OCRService()
    
    private init() {}
    
    func extractCertificateData(from image: UIImage) async -> CertificateData {
        guard let cgImage = image.cgImage else {
            return CertificateData()
        }
        
        return await withCheckedContinuation { continuation in
            var certificateData = CertificateData()
            
            // Create request for text recognition
            let request = VNRecognizeTextRequest { request, error in
                if error != nil {
                    continuation.resume(returning: CertificateData())
                    return
                }
                
                guard let observations = request.results as? [VNRecognizedTextObservation] else {
                    continuation.resume(returning: CertificateData())
                    return
                }
                
                var recognizedStrings: [String] = []
                for observation in observations {
                    guard let topCandidate = observation.topCandidates(1).first else {
                        continue
                    }
                    recognizedStrings.append(topCandidate.string)
                }
                
                // Process recognized text to extract certificate information
                certificateData = self.parseCertificateText(recognizedStrings)
                
                continuation.resume(returning: certificateData)
            }
            
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            
            // Perform the request
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(returning: CertificateData())
            }
        }
    }
    
    private func parseCertificateText(_ textLines: [String]) -> CertificateData {
        var data = CertificateData()
        let fullText = textLines.joined(separator: " ").uppercased()
        
        // Extract organization (PADI, SSI, NAUI, etc.)
        // Check for full organization names FIRST (more specific), then abbreviations
        // Order matters - more specific patterns should be checked first
        let organizationPatterns: [(pattern: String, name: String, isFullName: Bool)] = [
            // Full names first (most specific)
            ("AUSTRALIAN DIVER ACCREDITATION SCHEME", "ADAS", true),
            ("SCUBA SCHOOLS INTERNATIONAL", "SSI", true),
            ("PROFESSIONAL ASSOCIATION OF DIVING INSTRUCTORS", "PADI", true),
            ("NATIONAL ASSOCIATION OF UNDERWATER INSTRUCTORS", "NAUI", true),
            ("CONFEDERATION MONDIALE DES ACTIVITES SUBAQUATIQUES", "CMAS", true),
            ("BRITISH SUB AQUA CLUB", "BSAC", true),
            ("SCUBA DIVING INTERNATIONAL", "SDI", true),
            ("TECHNICAL DIVING INTERNATIONAL", "TDI", true),
            ("GLOBAL UNDERWATER EXPLORERS", "GUE", true),
            // Abbreviations (less specific, checked after full names)
            ("ADAS", "ADAS", false),
            ("SSI", "SSI", false),
            ("PADI", "PADI", false),
            ("NAUI", "NAUI", false),
            ("CMAS", "CMAS", false),
            ("BSAC", "BSAC", false),
            ("SDI", "SDI", false),
            ("TDI", "TDI", false),
            ("GUE", "GUE", false)
        ]
        
        // First pass: check full names only
        // Use flexible matching that allows words to appear in order with optional words between
        for (pattern, name, isFullName) in organizationPatterns where isFullName {
            // Create regex pattern that allows optional words between pattern words
            let patternWords = pattern.components(separatedBy: " ").filter { !$0.isEmpty }
            if patternWords.count > 0 {
                // Build regex: word1 (optional words) word2 (optional words) word3...
                let regexPattern = patternWords.map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "\\s+[^\\s]*\\s*")
                
                if let regex = try? NSRegularExpression(pattern: regexPattern, options: [.caseInsensitive]),
                   regex.firstMatch(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count)) != nil {
                    data.organization = name
                    break
                }
            }
            
            // Fallback: simple contains check after normalization
            let normalizedPattern = pattern.replacingOccurrences(of: "[^A-Z0-9\\s]", with: " ", options: .regularExpression)
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
            let normalizedText = fullText.replacingOccurrences(of: "[^A-Z0-9\\s]", with: " ", options: .regularExpression)
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
            
            if normalizedText.contains(normalizedPattern) {
                data.organization = name
                break
            }
        }
        
        // Second pass: check abbreviations only if no full name found
        if data.organization == nil {
            for (pattern, name, isFullName) in organizationPatterns where !isFullName {
                // For abbreviations, check if they appear as whole words (not part of other words)
                // Use word boundaries or check that pattern is surrounded by non-letter characters
                let wordBoundaryPattern = "(?:^|[^A-Z0-9])\(pattern)(?:[^A-Z0-9]|$)"
                if let regex = try? NSRegularExpression(pattern: wordBoundaryPattern, options: []),
                   let match = regex.firstMatch(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count)) {
                    // Get context around the match to verify it's not part of another word
                    let matchRange = match.range
                    _ = max(0, matchRange.location - 10)
                    _ = min(fullText.utf16.count, matchRange.location + matchRange.length + 10)
                    data.organization = name
                    break
                }
            }
        }
        
        // Extract certification level - use fuzzy matching and partial matches
        // Also handle common OCR errors (O vs 0, I vs 1, etc.)
        let levelPatterns: [(patterns: [String], display: String)] = [
            (["OPEN.*WATER", "OUE.*WATER", "OPEN.*WAT[R|E]", "PRO.*OPEN.*WATER", "PRO.*OUE.*WATER"], "Open Water"),
            (["ADVANCED.*OPEN.*WATER", "ADVANCED.*OUE.*WATER"], "Advanced Open Water"),
            (["ADVANCED.*DIVER"], "Advanced Diver"),
            (["RESCUE.*DIVER"], "Rescue Diver"),
            (["DIVE.*MASTER", "DIVEMASTER"], "Divemaster"),
            (["MASTER.*DIVER"], "Master Diver"),
            (["INSTRUCTOR", "INSTRUCT[R|O]R"], "Instructor"),
            (["MASTER.*INSTRUCTOR"], "Master Instructor"),
            (["COURSE.*DIRECTOR"], "Course Director"),
            (["ASSISTANT.*INSTRUCTOR"], "Assistant Instructor")
        ]
        
        for (patterns, display) in levelPatterns {
            for pattern in patterns {
                if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
                   regex.firstMatch(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count)) != nil {
                    data.level = display
                    break
                }
            }
            if data.level != nil {
                break
            }
        }
        
        // Also try fuzzy matching for common levels if exact match failed
        // Only if we have some text to work with
        // IMPORTANT: Require keywords to be close together (within 15 words) to avoid false positives
        if data.level == nil && !fullText.isEmpty {
            let fuzzyLevels: [(keywords: [String], display: String)] = [
                (["OPEN", "WATER"], "Open Water"),
                (["ADVANCED", "OPEN", "WATER"], "Advanced Open Water"),
                (["ADVANCED", "DIVER"], "Advanced Diver"),
                (["RESCUE", "DIVER"], "Rescue Diver"),
                (["DIVE", "MASTER"], "Divemaster"),
                (["MASTER", "DIVER"], "Master Diver"),
                (["INSTRUCTOR"], "Instructor")
            ]
            
            let words = fullText.components(separatedBy: CharacterSet.whitespacesAndNewlines).filter { !$0.isEmpty }
            
            for (keywords, display) in fuzzyLevels {
                // For single keyword levels (like "Instructor"), require exact word match (not fuzzy)
                // This prevents false positives where fuzzy matching finds the keyword in unrelated text
                if keywords.count == 1 {
                    let keyword = keywords[0]
                    // Check if keyword appears as a whole word (not part of another word)
                    let wordBoundaryPattern = "(?:^|[^A-Z0-9])\(keyword)(?:[^A-Z0-9]|$)"
                    if let regex = try? NSRegularExpression(pattern: wordBoundaryPattern, options: []),
                       regex.firstMatch(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count)) != nil {
                        data.level = display
                        break
                    }
                } else {
                    // For multi-keyword levels, require keywords to be close together (within 15 words)
                    var keywordPositions: [Int] = []
                    for keyword in keywords {
                        for (index, word) in words.enumerated() {
                            if word.contains(keyword) || fuzzyMatch(word, pattern: keyword) {
                                keywordPositions.append(index)
                                break
                            }
                        }
                    }
                    
                    // Check if we found all keywords and they are close together
                    if keywordPositions.count == keywords.count {
                        let minPos = keywordPositions.min() ?? 0
                        let maxPos = keywordPositions.max() ?? 0
                        let distance = maxPos - minPos
                        
                        // Require keywords to be within 15 words of each other
                        if distance <= 15 {
                            data.level = display
                            break
                        }
                    }
                }
            }
        }
        
        // Extract date (look for certificate issue date, not birth date)
        // Priority: CERT.DATE > ISSUE DATE > CERT DATE > DATE (but not D.O.B or BIRTHDATE)
        let issueDateKeywords = ["CERT\\.?DATE", "CERT DATE", "ISSUE DATE", "ISSUED", "CERTIFICATION DATE", "DATE:"]
        let excludeKeywords = ["D\\.?O\\.?B", "BIRTHDATE", "BIRTH DATE", "DATE OF BIRTH", "EXPIRY", "EXPIRES", "VALID UNTIL"]
        let datePatterns = [
            "\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}",  // MM/DD/YYYY or DD/MM/YYYY
            "\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}",  // YYYY/MM/DD
            "\\d{1,2}-[A-Z]{3}-\\d{4}",  // DD-MON-YYYY (e.g., 10-OCT-2010)
            "[A-Z]{3}\\s+\\d{1,2},?\\s+\\d{4}",  // MON DD, YYYY
            "\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4}"  // DD MON YYYY
        ]
        
        // First try to find date near issue date keywords (highest priority)
        for keyword in issueDateKeywords {
            if let keywordRange = fullText.range(of: keyword, options: .regularExpression) {
                // Check if this keyword is not part of an excluded phrase
                let contextStart = max(0, fullText.distance(from: fullText.startIndex, to: keywordRange.lowerBound) - 20)
                let contextEnd = min(fullText.count, fullText.distance(from: fullText.startIndex, to: keywordRange.upperBound) + 50)
                let contextRange = fullText.index(fullText.startIndex, offsetBy: contextStart)..<fullText.index(fullText.startIndex, offsetBy: contextEnd)
                let context = String(fullText[contextRange]).uppercased()
                
                var isExcluded = false
                for exclude in excludeKeywords {
                    if context.contains(exclude) {
                        isExcluded = true
                        break
                    }
                }
                
                if !isExcluded {
                    let searchStart = keywordRange.upperBound
                    let searchText = String(fullText[searchStart...])
                    
                    for pattern in datePatterns {
                        if let regex = try? NSRegularExpression(pattern: pattern, options: []),
                           let match = regex.firstMatch(in: searchText, options: [], range: NSRange(location: 0, length: searchText.utf16.count)),
                           let range = Range(match.range, in: searchText) {
                            let dateString = String(searchText[range])
                            if let date = parseDate(dateString) {
                                data.issueDate = date
                                break
                            }
                        }
                    }
                    if data.issueDate != nil {
                        break
                    }
                }
            }
        }
        
        // If no date found near keywords, try general search but exclude birth dates
        if data.issueDate == nil {
            var allDates: [(date: Date, string: String, position: Int)] = []
            
            for pattern in datePatterns {
                if let regex = try? NSRegularExpression(pattern: pattern, options: []) {
                    let matches = regex.matches(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count))
                    for match in matches {
                        if let range = Range(match.range, in: fullText) {
                            let dateString = String(fullText[range])
                            if let date = parseDate(dateString) {
                                // Check context around this date
                                let contextStart = max(0, match.range.location - 30)
                                let contextEnd = min(fullText.utf16.count, match.range.location + match.range.length + 30)
                                let contextRange = NSRange(location: contextStart, length: contextEnd - contextStart)
                                if let context = Range(contextRange, in: fullText) {
                                    let contextText = String(fullText[context]).uppercased()
                                    var isExcluded = false
                                    for exclude in excludeKeywords {
                                        if contextText.contains(exclude) {
                                            isExcluded = true
                                            break
                                        }
                                    }
                                    if !isExcluded {
                                        allDates.append((date: date, string: dateString, position: match.range.location))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Prefer dates that are more recent (likely issue dates, not birth dates)
            if !allDates.isEmpty {
                let sortedDates = allDates.sorted { $0.date > $1.date }
                data.issueDate = sortedDates.first?.date
            }
        }
        
        // Extract instructor number (look for "INSTR.NO", "INSTRUCTOR NO", "INSTRUCTOR NUMBER", etc.)
        let instructorPatterns = [
            "INSTR\\.?NO[>.:]?\\s*([A-Z0-9-]+)",
            "INSTRUCTOR\\s+NO[>.:]?\\s*([A-Z0-9-]+)",
            "INSTRUCTOR\\s+NUMBER[>.:]?\\s*([A-Z0-9-]+)",
            "INSTRUCTOR.*?\\s+([A-Z]{2,4}-?\\d+)"
        ]
        
        for pattern in instructorPatterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: fullText, options: [], range: NSRange(location: 0, length: fullText.utf16.count)),
               match.numberOfRanges > 1,
               let range = Range(match.range(at: 1), in: fullText) {
                let instructorNum = String(fullText[range]).trimmingCharacters(in: .whitespaces)
                if !instructorNum.isEmpty {
                    data.instructorNumber = instructorNum
                    break
                }
            }
        }
        
        return data
    }
    
    private func parseDate(_ dateString: String) -> Date? {
        let formats = [
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "MM-dd-yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "dd-MMM-yyyy",  // 10-OCT-2010
            "dd MMM yyyy",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "dd MMMM yyyy"
        ]
        
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        
        for format in formats {
            dateFormatter.dateFormat = format
            if let date = dateFormatter.date(from: dateString.uppercased()) {
                return date
            }
        }
        
        return nil
    }
    
    // Fuzzy matching helper for OCR errors
    private func fuzzyMatch(_ text: String, pattern: String) -> Bool {
        // Check if pattern characters appear in order in text (allowing for OCR errors)
        var patternIndex = pattern.startIndex
        for char in text {
            if patternIndex < pattern.endIndex && char == pattern[patternIndex] {
                patternIndex = pattern.index(after: patternIndex)
            }
        }
        // If we matched at least 70% of the pattern, consider it a match
        let matchedRatio = Double(pattern.distance(from: pattern.startIndex, to: patternIndex)) / Double(pattern.count)
        return matchedRatio >= 0.7
    }
}
