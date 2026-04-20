//
//  PhotoProcessingView.swift
//  DiveHub
//
//  Улучшение подводных фото (серверное).
//

import PhotosUI
import SwiftUI
import UIKit

struct PhotoProcessingView: View {
    @StateObject private var localizationService = LocalizationService.shared

    @State private var mediaItem: PhotosPickerItem?
    @State private var sourceImage: UIImage?
    @State private var processedImage: UIImage?
    @State private var isProcessing = false
    @State private var isLoadingMedia = false
    @State private var moduleReachable: Bool?
    @State private var errorMessage: String?
    @State private var showShare = false
    @State private var shareItems: [Any] = []
    @State private var strength: Double = NetworkService.cardLookProfile.strength
    @State private var useDepth = false
    @State private var depthMeters: Double = 10
    /// Принудительное обновление превью «После» (SwiftUI иногда не перерисовывает Image).
    @State private var processedResultID = UUID()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(localizationService.localizedString("photoAIIntro", table: "imageEditing"))
                        .font(.body)
                        .foregroundColor(.diveTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    if moduleReachable == false {
                        offlineServiceBanner
                    }

                    PhotosPicker(
                        selection: $mediaItem,
                        matching: .images,
                        photoLibrary: .shared()
                    ) {
                        HStack {
                            if isLoadingMedia {
                                ProgressView()
                            }
                            Label(
                                localizationService.localizedString("diveEditorChoosePhoto", table: "imageEditing"),
                                systemImage: "photo.on.rectangle.angled"
                            )
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.divePrimary)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                    .disabled(isLoadingMedia)

                    videoComingSoonTeaser

                    if sourceImage != nil {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(localizationService.localizedString("strengthProcessing", table: "imageEditing"))
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(.diveText)
                            HStack {
                                Slider(value: $strength, in: 0.3...1.0, step: 0.05)
                                Text("\(Int(strength * 100))%")
                                    .font(.subheadline.weight(.medium).monospacedDigit())
                                    .foregroundColor(.diveTextSecondary)
                                    .frame(minWidth: 44, alignment: .trailing)
                            }
                        }

                        Toggle(localizationService.localizedString("depthHintUse", table: "imageEditing"), isOn: $useDepth)
                            .tint(.divePrimary)
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

                        if isProcessing {
                            HStack(spacing: 10) {
                                ProgressView()
                                Text(localizationService.localizedString("processingPleaseWait", table: "imageEditing"))
                                    .font(.subheadline)
                                    .foregroundColor(.diveTextSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 4)
                        }

                        resultBlock
                    }
                }
                .padding()
            }
            .background(Color.diveBackground)
            .navigationTitle(localizationService.localizedString("photoProcessing", table: "common"))
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showShare) {
                ShareSheet(items: shareItems)
            }
            .onChange(of: sourceImage) { _, new in
                if new != nil {
                    processedImage = nil
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

    private var offlineServiceBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "wifi.exclamationmark")
                .font(.title3)
                .foregroundColor(.orange)
            Text(localizationService.localizedString("photoProcessingServiceUnavailable", table: "imageEditing"))
                .font(.subheadline)
                .foregroundColor(.diveText)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.12))
        .cornerRadius(12)
    }

    private var videoComingSoonTeaser: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "video.fill")
                .font(.title3)
                .foregroundColor(.divePrimary)
                .frame(width: 28, alignment: .center)
            VStack(alignment: .leading, spacing: 4) {
                Text(localizationService.localizedString("underwaterVideoProcessingTeaserTitle", table: "imageEditing"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.diveText)
                Text(localizationService.localizedString("underwaterVideoProcessingTeaserSubtitle", table: "imageEditing"))
                    .font(.caption)
                    .foregroundColor(.diveTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.divePrimary.opacity(0.08))
        .cornerRadius(12)
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
                }
                .font(.caption)
                .foregroundColor(.diveTextSecondary)
            }
        }
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
                Text(localizationService.localizedString("processPhotoServer", table: "imageEditing"))
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(sourceImage == nil || isProcessing ? Color.gray : Color.divePrimary)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(sourceImage == nil || isProcessing)
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
            }
            return
        }

        await MainActor.run {
            errorMessage = localizationService.localizedString("errorPhotoProcessing", table: "imageEditing")
        }
    }

    private func runAutomaticProcessing() async {
        await MainActor.run {
            isProcessing = true
            errorMessage = nil
        }
        do {
            guard let src = sourceImage else {
                throw NetworkError.noData
            }
            guard let jpeg = jpegDataForUpload(from: src) else {
                throw NetworkError.decodingError
            }
            let out = try await NetworkService.shared.processPhotoUnderwaterVisionModule(
                imageJPEG: jpeg,
                engine: NetworkService.cardLookProfile.engine,
                strength: strength,
                depthHintMeters: useDepth ? depthMeters : nil,
                mode: NetworkService.cardLookProfile.mode
            )
            guard let img = UIImage(data: out) else {
                throw NetworkError.decodingError
            }
            await MainActor.run {
                processedImage = img
                processedResultID = UUID()
                isProcessing = false
            }
        } catch {
            await MainActor.run {
                isProcessing = false
                errorMessage = NetworkError.userFacingMessage(error)
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
