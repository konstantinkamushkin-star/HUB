//
//  DiveEditorTabView.swift
//  DiveHub
//

import PhotosUI
import SwiftUI
import UIKit

private struct DiveEditorImagePayload: Identifiable {
    let id = UUID()
    let image: UIImage
}

struct DiveEditorTabView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var mediaItem: PhotosPickerItem?
    @State private var isLoadingPick = false
    @State private var errorMessage: String?
    @State private var showAppGallery = false
    @State private var editorPayload: DiveEditorImagePayload?
    @State private var recent: [DiveEditorRecentEntry] = []

    var body: some View {
        ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(localizationService.localizedString("diveEditorHeadline", table: "imageEditing"))
                        .font(.title2.bold())
                        .foregroundColor(.diveText)

                    Text(localizationService.localizedString("diveEditorIntro", table: "imageEditing"))
                        .font(.subheadline)
                        .foregroundColor(.diveTextSecondary)

                    VStack(spacing: 12) {
                        PhotosPicker(
                            selection: $mediaItem,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            Label(
                                localizationService.localizedString("diveEditorChoosePhoto", table: "imageEditing"),
                                systemImage: "photo.on.rectangle.angled"
                            )
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(Color.divePrimary)
                            .foregroundColor(.white)
                            .cornerRadius(14)
                        }
                        .buttonStyle(.plain)
                        .disabled(isLoadingPick)

                        Button {
                            showAppGallery = true
                        } label: {
                            Label(localizationService.localizedString("diveEditorAppGallery", table: "imageEditing"), systemImage: "square.grid.2x2")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(Color.diveCard)
                                .foregroundColor(.diveText)
                                .cornerRadius(12)
                        }

                        diveEditorVideoComingSoonTeaser
                    }

                    diveEditorSampleBlock

                    if !recent.isEmpty {
                        Text(localizationService.localizedString("diveEditorRecent", table: "imageEditing"))
                            .font(.headline)
                            .foregroundColor(.diveText)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 8)], spacing: 8) {
                            ForEach(recent) { entry in
                                let url = DiveEditorRecentStore.shared.thumbnailURL(for: entry)
                                if let data = try? Data(contentsOf: url), let ui = UIImage(data: data) {
                                    Image(uiImage: ui)
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 96, height: 96)
                                        .clipped()
                                        .cornerRadius(10)
                                }
                            }
                        }
                    }
                }
                .padding()
            }
            .background(Color.diveBackground)
            .navigationTitle(localizationService.localizedString("diveEditorTabTitle", table: "imageEditing"))
            .diveHubNavigationChrome()
            .onAppear {
                recent = DiveEditorRecentStore.shared.loadEntries()
                DiveEditorAnalyticsService.shared.track(.underwaterTabOpened)
            }
            .onChange(of: mediaItem) { _, new in
                Task { await loadMediaFromPhotosPicker(new) }
            }
            .sheet(isPresented: $showAppGallery) {
                DiveEditorAppGallerySheet { img in
                    editorPayload = DiveEditorImagePayload(image: img)
                }
            }
            .fullScreenCover(item: $editorPayload) { payload in
                NavigationStack {
                    DiveEditorEditorView(image: payload.image) {
                        editorPayload = nil
                        recent = DiveEditorRecentStore.shared.loadEntries()
                    }
                }
            }
            .overlay {
                if isLoadingPick {
                    ZStack {
                        Color.black.opacity(0.2).ignoresSafeArea()
                        ProgressView()
                            .scaleEffect(1.2)
                    }
                }
            }
            .alert(localizationService.localizedString("error", table: "common"), isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button(localizationService.localizedString("ok", table: "common"), role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
    }

    private var diveEditorVideoComingSoonTeaser: some View {
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

    private var diveEditorSampleBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizationService.localizedString("diveEditorSampleTitle", table: "imageEditing"))
                .font(.headline)
            HStack(spacing: 12) {
                sampleCard(title: localizationService.localizedString("before", table: "imageEditing"), systemImage: "photo")
                Image(systemName: "arrow.right")
                    .foregroundColor(.diveTextSecondary)
                sampleCard(title: localizationService.localizedString("after", table: "imageEditing"), systemImage: "wand.and.stars")
            }
        }
        .padding()
        .background(Color.diveCard)
        .cornerRadius(14)
    }

    private func sampleCard(title: String, systemImage: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundColor(.divePrimary)
            Text(title)
                .font(.caption)
                .foregroundColor(.diveTextSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private func loadMediaFromPhotosPicker(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        await MainActor.run { isLoadingPick = true }
        defer {
            Task { @MainActor in
                isLoadingPick = false
                mediaItem = nil
            }
        }

        guard let data = try? await item.loadTransferable(type: Data.self) else {
            await MainActor.run {
                errorMessage = LocalizationService.shared.localizedString("unsupportedFileFormat", table: "imageEditing")
            }
            return
        }

        if let ui = UIImage(data: data) {
            await MainActor.run {
                DiveEditorAnalyticsService.shared.track(.photoSelected, fileType: "photos_picker")
                editorPayload = DiveEditorImagePayload(image: ui)
            }
            return
        }

        await MainActor.run {
            errorMessage = LocalizationService.shared.localizedString("unsupportedFileFormat", table: "imageEditing")
        }
    }
}

#Preview {
    NavigationStack {
        DiveEditorTabView()
    }
}
