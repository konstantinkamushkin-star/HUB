//
//  DiveEditorAppGallerySheet.swift
//  DiveHub
//

import SwiftUI
import UIKit

/// Photos already attached to dive logs (URLs) + recent Dive Editor exports.
struct DiveEditorAppGallerySheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var logbook = LogbookViewModel()
    var onPick: (UIImage) -> Void

    private var remotePhotoURLs: [URL] {
        logbook.diveLogs.flatMap(\.photos).compactMap { path -> URL? in
            if path.hasPrefix("http") { return URL(string: path) }
            return URL(string: NetworkService.shared.baseURL + (path.hasPrefix("/") ? path : "/" + path))
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section(localizationService.localizedString("diveEditorAppGalleryLogPhotos", table: "imageEditing")) {
                    if remotePhotoURLs.isEmpty {
                        Text(localizationService.localizedString("diveEditorNoLogPhotos", table: "imageEditing"))
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(Array(remotePhotoURLs.enumerated()), id: \.offset) { _, url in
                            Button {
                                Task { await loadRemote(url) }
                            } label: {
                                HStack {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let i):
                                            i.resizable().scaledToFill()
                                        default:
                                            Color.gray.opacity(0.2)
                                        }
                                    }
                                    .frame(width: 56, height: 56)
                                    .clipped()
                                    .cornerRadius(8)
                                    Text(url.lastPathComponent)
                                        .lineLimit(1)
                                        .foregroundColor(.primary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("diveEditorAppGallery", table: "imageEditing"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizationService.localizedString("cancel", table: "common")) { dismiss() }
                }
            }
            .task {
                await logbook.loadLogs()
            }
        }
    }

    private func loadRemote(_ url: URL) async {
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard let img = UIImage(data: data) else { return }
            await MainActor.run {
                DiveEditorAnalyticsService.shared.track(.photoSelected, fileType: "app_gallery_url")
                onPick(img)
                dismiss()
            }
        } catch {
            await MainActor.run { dismiss() }
        }
    }
}
