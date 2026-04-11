//
//  PhotoProcessingView.swift
//  DiveHub
//
//  Вкладка: обработка фото через underwater-vision-module (FastAPI).
//

import AVFoundation
import AVKit
import Photos
import PhotosUI
import SwiftUI
import UIKit

private enum PhotoProcessingPickMedia {
    static let maxVideoDurationSeconds: Double = 5 * 60

    static func videoWithinDurationLimit(url: URL) async -> Bool {
        let asset = AVURLAsset(url: url)
        do {
            let duration = try await asset.load(.duration)
            let s = CMTimeGetSeconds(duration)
            return s.isFinite && !s.isNaN && s > 0 && s <= maxVideoDurationSeconds
        } catch {
            return false
        }
    }
}

struct PhotoProcessingView: View {
    /// Видео: `ai2` (другой алгоритм слота, чем фото `ai1`) + `luma_boost` / `max_side`.
    private static let underwaterVideoEngine = "ai2"

    @StateObject private var localizationService = LocalizationService.shared
    @AppStorage(NetworkService.underwaterVisionModuleBaseURLKey) private var moduleURLStorage: String = ""

    @State private var mediaItem: PhotosPickerItem?
    @State private var sourceImage: UIImage?
    @State private var sourceVideoURL: URL?
    @State private var processedImage: UIImage?
    @State private var processedVideoURL: URL?
    @State private var isProcessing = false
    @State private var isLoadingMedia = false
    @State private var moduleReachable: Bool?
    @State private var errorMessage: String?
    @State private var showShare = false
    @State private var shareItems: [Any] = []
    @State private var strength: Double = 0.7
    @State private var useDepth = false
    @State private var depthMeters: Double = 10
    @State private var showURLField = false
    /// Принудительное обновление превью «После» (SwiftUI иногда не перерисовывает Image).
    @State private var processedResultID = UUID()
    @State private var lastUsedEngine: String = ""
    @State private var videoProcessProgress: VideoUnderwaterProcessingProgress?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(localizationService.localizedString("photoAIIntro", table: "imageEditing"))
                        .font(.subheadline)
                        .foregroundColor(.diveTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    connectionBlock

                    DisclosureGroup(isExpanded: $showURLField) {
                        TextField(
                            "http://127.0.0.1:8010",
                            text: $moduleURLStorage
                        )
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        Text(localizationService.localizedString("visionModuleURLHint", table: "imageEditing"))
                            .font(.caption)
                            .foregroundColor(.diveTextSecondary)
                    } label: {
                        Label(localizationService.localizedString("visionModuleURL", table: "imageEditing"), systemImage: "network")
                            .font(.subheadline)
                    }

                    PhotosPicker(
                        selection: $mediaItem,
                        matching: .any(of: [.images, .videos]),
                        photoLibrary: .shared()
                    ) {
                        HStack {
                            if isLoadingMedia {
                                ProgressView()
                            }
                            Label(
                                localizationService.localizedString("photoOrVideoFromLibrary", table: "imageEditing"),
                                systemImage: "photo.on.rectangle.angled"
                            )
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.divePrimary.opacity(0.15))
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoadingMedia)

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizationService.localizedString("strengthProcessing", table: "imageEditing"))
                            .font(.caption)
                            .foregroundColor(.diveTextSecondary)
                        HStack {
                            Slider(value: $strength, in: 0.3...1.0, step: 0.05)
                            Text(String(format: "%.2f", strength))
                                .font(.caption.monospacedDigit())
                                .foregroundColor(.diveTextSecondary)
                                .frame(width: 44, alignment: .trailing)
                        }
                    }

                    Toggle(localizationService.localizedString("depthHintUse", table: "imageEditing"), isOn: $useDepth)
                    if useDepth {
                        HStack {
                            Text(localizationService.localizedString("depthHintMetersLabel", table: "imageEditing"))
                            Spacer()
                            Stepper(value: $depthMeters, in: 0...60, step: 1) {
                                Text("\(Int(depthMeters)) m")
                            }
                        }
                    }

                    automaticProcessingButton

                    if isProcessing, sourceVideoURL != nil, let vp = videoProcessProgress {
                        VideoUnderwaterProgressBanner(progress: vp)
                    }
                    if isProcessing, sourceVideoURL == nil {
                        Text(localizationService.localizedString("processingPleaseWait", table: "imageEditing"))
                            .font(.caption)
                            .foregroundColor(.diveTextSecondary)
                    }

                    resultBlock
                }
                .padding()
            }
            .background(Color.diveBackground)
            .navigationTitle(localizationService.localizedString("photoProcessing", table: "common"))
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showShare) {
                ShareSheet(items: shareItems)
            }
            .onChange(of: sourceImage) { _, new in
                if new != nil {
                    processedImage = nil
                    sourceVideoURL = nil
                    processedVideoURL = nil
                }
            }
            .onChange(of: sourceVideoURL) { _, new in
                if new != nil {
                    sourceImage = nil
                    processedImage = nil
                    processedVideoURL = nil
                    lastUsedEngine = ""
                }
            }
            .onChange(of: mediaItem) { _, new in
                Task { await loadMediaFromLibrary(new) }
            }
            .alert(localizationService.localizedString("error", table: "common"), isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button(localizationService.localizedString("ok", table: "common"), role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
            .task {
                await refreshHealth()
            }
        }
    }

    private var connectionBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizationService.localizedString("visionModuleURL", table: "imageEditing"))
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.diveTextSecondary)
                    Text(NetworkService.underwaterVisionModuleBaseURLString())
                        .font(.caption2.monospaced())
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                Spacer(minLength: 8)
                statusBadge
            }
            Button(localizationService.localizedString("pingVisionModule", table: "imageEditing")) {
                Task { await refreshHealth() }
            }
            .buttonStyle(.bordered)
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private var statusBadge: some View {
        let (text, color) = moduleStatusLabel
        return Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.2))
            .foregroundColor(color)
            .cornerRadius(8)
    }

    private var moduleStatusLabel: (String, Color) {
        switch moduleReachable {
        case true:
            return (localizationService.localizedString("moduleStatusOnline", table: "imageEditing"), .green)
        case false:
            return (localizationService.localizedString("moduleStatusOffline", table: "imageEditing"), .orange)
        case nil:
            return (localizationService.localizedString("moduleStatusUnknown", table: "imageEditing"), .secondary)
        }
    }

    @ViewBuilder
    private var resultBlock: some View {
        if let src = sourceImage {
            Text(localizationService.localizedString("resultCompare", table: "imageEditing"))
                .font(.headline)
                .padding(.top, 8)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizationService.localizedString("before", table: "imageEditing"))
                            .font(.caption)
                            .foregroundColor(.diveTextSecondary)
                        Image(uiImage: src)
                            .resizable()
                            .scaledToFit()
                            .frame(height: 220)
                            .cornerRadius(10)
                    }
                    if let out = processedImage {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(localizationService.localizedString("after", table: "imageEditing"))
                                .font(.caption)
                                .foregroundColor(.diveTextSecondary)
                            Text(engineCaption(lastUsedEngine))
                                .font(.caption2)
                                .foregroundColor(.diveTextSecondary)
                            Image(uiImage: out)
                                .resizable()
                                .scaledToFit()
                                .frame(height: 220)
                                .cornerRadius(10)
                        }
                    }
                }
            }
            .id(processedResultID)
            if processedImage != nil {
                HStack(spacing: 12) {
                    Button {
                        shareItems = [processedImage!]
                        showShare = true
                    } label: {
                        Label(localizationService.localizedString("shareProcessed", table: "imageEditing"), systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.divePrimary)

                    Button {
                        UIImageWriteToSavedPhotosAlbum(processedImage!, nil, nil, nil)
                    } label: {
                        Label(localizationService.localizedString("saveToPhotos", table: "imageEditing"), systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                }
                Button(localizationService.localizedString("clearResult", table: "imageEditing")) {
                    processedImage = nil
                    lastUsedEngine = ""
                }
                .font(.caption)
                .foregroundColor(.diveTextSecondary)
            }
        } else if let srcVideo = sourceVideoURL {
            Text(localizationService.localizedString("resultCompare", table: "imageEditing"))
                .font(.headline)
                .padding(.top, 8)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizationService.localizedString("before", table: "imageEditing"))
                            .font(.caption)
                            .foregroundColor(.diveTextSecondary)
                        VideoPlayer(player: AVPlayer(url: srcVideo))
                            .frame(width: 220, height: 220)
                            .cornerRadius(10)
                    }
                    if let outVideo = processedVideoURL {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(localizationService.localizedString("after", table: "imageEditing"))
                                .font(.caption)
                                .foregroundColor(.diveTextSecondary)
                            Text(engineCaption(lastUsedEngine))
                                .font(.caption2)
                                .foregroundColor(.diveTextSecondary)
                            VideoPlayer(player: AVPlayer(url: outVideo))
                                .frame(width: 220, height: 220)
                                .cornerRadius(10)
                        }
                    }
                }
            }
            .id(processedResultID)
            if processedVideoURL != nil {
                HStack(spacing: 12) {
                    Button {
                        guard let outVideo = processedVideoURL else { return }
                        shareItems = [outVideo]
                        showShare = true
                    } label: {
                        Label(localizationService.localizedString("shareProcessed", table: "imageEditing"), systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.divePrimary)

                    Button {
                        guard let outVideo = processedVideoURL else { return }
                        UISaveVideoAtPathToSavedPhotosAlbum(outVideo.path, nil, nil, nil)
                    } label: {
                        Label(localizationService.localizedString("saveToPhotos", table: "imageEditing"), systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                }
                Button(localizationService.localizedString("clearResult", table: "imageEditing")) {
                    processedVideoURL = nil
                    lastUsedEngine = ""
                }
                .font(.caption)
                .foregroundColor(.diveTextSecondary)
            }
        }
    }

    private func engineCaption(_ engine: String) -> String {
        if engine == "ai1" || engine == "ai2" || engine == "cursor" || engine == Self.underwaterVideoEngine {
            return localizationService.localizedString("diveEditorAutomaticProcessing", table: "imageEditing")
        }
        return engine
    }

    private var automaticProcessingButton: some View {
        Button {
            Task { await runAutomaticProcessing() }
        } label: {
            HStack {
                if isProcessing {
                    ProgressView()
                        .tint(.white)
                }
                Text(localizationService.localizedString("diveEditorAutomaticProcessing", table: "imageEditing"))
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background((sourceImage == nil && sourceVideoURL == nil) || isProcessing ? Color.gray : Color.divePrimary)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled((sourceImage == nil && sourceVideoURL == nil) || isProcessing)
    }

    private func refreshHealth() async {
        let ok = await NetworkService.shared.checkUnderwaterVisionModuleHealth()
        await MainActor.run { moduleReachable = ok }
    }

    private func loadMediaFromLibrary(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        await MainActor.run { isLoadingMedia = true }
        defer {
            Task { @MainActor in
                isLoadingMedia = false
                mediaItem = nil
            }
        }

        guard let data = try? await item.loadTransferable(type: Data.self) else {
            await MainActor.run {
                errorMessage = localizationService.localizedString("errorPhotoProcessing", table: "imageEditing")
            }
            return
        }

        if let ui = UIImage(data: data) {
            await MainActor.run {
                sourceImage = ui
                processedImage = nil
                sourceVideoURL = nil
                processedVideoURL = nil
                lastUsedEngine = ""
            }
            return
        }

        let outURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("photo_processing_\(UUID().uuidString).mp4")
        do {
            try data.write(to: outURL, options: .atomic)
        } catch {
            await MainActor.run {
                errorMessage = localizationService.localizedString("errorPhotoProcessing", table: "imageEditing")
            }
            return
        }

        guard await PhotoProcessingPickMedia.videoWithinDurationLimit(url: outURL) else {
            try? FileManager.default.removeItem(at: outURL)
            await MainActor.run {
                errorMessage = localizationService.localizedString("videoMaxLength5Minutes", table: "imageEditing")
            }
            return
        }

        await MainActor.run {
            sourceVideoURL = outURL
            sourceImage = nil
            processedImage = nil
            processedVideoURL = nil
            lastUsedEngine = ""
        }
    }

    private func sourceVideoDurationSeconds(url: URL) async -> TimeInterval? {
        let asset = AVURLAsset(url: url)
        do {
            let d = try await asset.load(.duration)
            let s = CMTimeGetSeconds(d)
            return (s.isFinite && !s.isNaN && s > 0) ? s : nil
        } catch {
            return nil
        }
    }

    private func runAutomaticProcessing() async {
        await MainActor.run {
            isProcessing = true
            errorMessage = nil
            videoProcessProgress = sourceVideoURL != nil
                ? VideoUnderwaterProcessingProgress(fraction01: 0, estimatedSecondsRemaining: 0)
                : nil
        }
        do {
            let out: Data
            if let src = sourceImage {
                guard let jpeg = jpegDataForUpload(from: src) else {
                    throw NetworkError.decodingError
                }
                out = try await NetworkService.shared.processPhotoUnderwaterVisionModule(
                    imageJPEG: jpeg,
                    engine: "ai1",
                    strength: strength,
                    depthHintMeters: useDepth ? depthMeters : nil
                )
                guard let img = UIImage(data: out) else {
                    throw NetworkError.decodingError
                }
                await MainActor.run {
                    processedImage = img
                    processedVideoURL = nil
                    lastUsedEngine = "ai1"
                    processedResultID = UUID()
                    isProcessing = false
                }
                return
            }
            if let srcVideo = sourceVideoURL {
                let duration = await sourceVideoDurationSeconds(url: srcVideo)
                let videoData = try Data(contentsOf: srcVideo)
                out = try await NetworkService.shared.processVideoUnderwaterVisionModule(
                    videoData: videoData,
                    engine: Self.underwaterVideoEngine,
                    strength: strength,
                    depthHintMeters: useDepth ? depthMeters : nil,
                    sourceVideoDuration: duration,
                    progress: { p in
                        videoProcessProgress = p
                    }
                )
                let outURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent("processed_\(UUID().uuidString).mp4")
                try out.write(to: outURL, options: .atomic)
                await MainActor.run {
                    videoProcessProgress = nil
                    processedVideoURL = outURL
                    processedImage = nil
                    lastUsedEngine = Self.underwaterVideoEngine
                    processedResultID = UUID()
                    isProcessing = false
                }
                return
            }
            throw NetworkError.noData
        } catch {
            await MainActor.run {
                videoProcessProgress = nil
                isProcessing = false
                errorMessage = error.localizedDescription
            }
        }
    }

    /// JPEG для загрузки; длинная сторона не больше `maxSide`.
    private func jpegDataForUpload(from image: UIImage, maxSide: CGFloat = 2048) -> Data? {
        let m = max(image.size.width, image.size.height)
        let toDraw: UIImage
        if m > maxSide {
            let scale = maxSide / m
            let sz = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let renderer = UIGraphicsImageRenderer(size: sz)
            toDraw = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: sz))
            }
        } else {
            toDraw = image
        }
        return toDraw.jpegData(compressionQuality: 0.92)
    }
}

#Preview {
    PhotoProcessingView()
}
