//
//  DiveEditorRecentStore.swift
//  DiveHub
//

import Foundation
import UIKit

struct DiveEditorRecentEntry: Identifiable, Codable, Equatable {
    let id: String
    let createdAt: Date
    let thumbnailFilename: String
}

final class DiveEditorRecentStore {
    static let shared = DiveEditorRecentStore()

    private let fm = FileManager.default
    private let entriesKey = "dive_editor_recent_entries"
    private var rootDir: URL {
        let doc = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return doc.appendingPathComponent("DiveEditor", isDirectory: true)
    }

    private init() {
        try? fm.createDirectory(at: rootDir, withIntermediateDirectories: true)
    }

    func loadEntries() -> [DiveEditorRecentEntry] {
        guard let data = UserDefaults.standard.data(forKey: entriesKey),
              let list = try? JSONDecoder().decode([DiveEditorRecentEntry].self, from: data) else {
            return []
        }
        return list.sorted { $0.createdAt > $1.createdAt }
    }

    func thumbnailURL(for entry: DiveEditorRecentEntry) -> URL {
        rootDir.appendingPathComponent(entry.thumbnailFilename)
    }

    /// Saves full JPEG and a small thumbnail; returns new entry.
    func saveExport(fullImage: UIImage, jpegQuality: CGFloat = 0.92) throws -> DiveEditorRecentEntry {
        let id = UUID().uuidString
        let thumbSize: CGFloat = 200
        let thumb = fullImage.downscaled(maxSide: thumbSize) ?? fullImage
        guard let fullData = fullImage.jpegData(compressionQuality: jpegQuality),
              let thumbData = thumb.jpegData(compressionQuality: 0.82) else {
            throw NSError(domain: "DiveEditorRecentStore", code: 1, userInfo: [NSLocalizedDescriptionKey: "encode failed"])
        }
        let fullName = "\(id)_full.jpg"
        let thumbName = "\(id)_thumb.jpg"
        try fullData.write(to: rootDir.appendingPathComponent(fullName))
        try thumbData.write(to: rootDir.appendingPathComponent(thumbName))

        let entry = DiveEditorRecentEntry(id: id, createdAt: Date(), thumbnailFilename: thumbName)
        var list = loadEntries()
        list.insert(entry, at: 0)
        if list.count > 24 { list = Array(list.prefix(24)) }
        let data = try JSONEncoder().encode(list)
        UserDefaults.standard.set(data, forKey: entriesKey)
        return entry
    }
}

private extension UIImage {
    func downscaled(maxSide: CGFloat) -> UIImage? {
        let m = max(size.width, size.height)
        guard m > maxSide else { return self }
        let scale = maxSide / m
        let nw = size.width * scale
        let nh = size.height * scale
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: nw, height: nh))
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: CGSize(width: nw, height: nh))) }
    }
}
