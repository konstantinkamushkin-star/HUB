//
//  DiveEditorEditorView.swift
//  DiveHub
//

import Photos
import SwiftUI
import UIKit

struct DiveEditorEditorView: View {
    let image: UIImage
    var onDismiss: () -> Void

    @StateObject private var vm = UnderwaterPhotoEditorViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showSaveMenu = false
    @State private var showShare = false
    @State private var shareItems: [Any] = []
    @State private var showReplaceConfirm = false
    @State private var manualSliderDebounce: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 0) {
            compareArea
                .frame(maxHeight: .infinity)

            if let msg = vm.diveEditorLongWaitMessage {
                Text(msg)
                    .font(.caption)
                    .foregroundColor(.orange)
                    .padding(8)
                    .frame(maxWidth: .infinity)
                    .background(Color.orange.opacity(0.12))
            }
            if let ai = vm.aiStatusMessage {
                Text(ai)
                    .font(.caption2)
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
            }

            Picker("", selection: $vm.diveEditorCompare) {
                Text(localizationService.localizedString("diveEditorCompareAfter", table: "imageEditing")).tag(DiveEditorCompareMode.after)
                Text(localizationService.localizedString("diveEditorCompareBefore", table: "imageEditing")).tag(DiveEditorCompareMode.before)
                Text(localizationService.localizedString("diveEditorCompareSplit", table: "imageEditing")).tag(DiveEditorCompareMode.split)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            Button {
                DiveEditorAnalyticsService.shared.track(.autoAiStarted, processingMode: "cloud_then_fallback_article")
                vm.runDiveEditorAutomaticProcessing()
            } label: {
                HStack {
                    if vm.isProcessing {
                        ProgressView().tint(.white)
                    }
                    Text(localizationService.localizedString("diveEditorAutomaticProcessing", table: "imageEditing"))
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.divePrimary)
                .foregroundColor(.white)
                .cornerRadius(12)
            }
            .disabled(vm.isProcessing)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    diveSlider(
                        localizationService.localizedString("diveEditorDepth", table: "imageEditing"),
                        value: $vm.diveEditorDepth,
                        range: 0...100,
                        disabled: !vm.diveEditorSlidersEnabled
                    )
                    diveSlider(
                        localizationService.localizedString("diveEditorColorStrength", table: "imageEditing"),
                        value: $vm.diveEditorColorStrength,
                        range: 0...100,
                        disabled: !vm.diveEditorSlidersEnabled
                    )
                    diveSlider(
                        localizationService.localizedString("diveEditorDehaze", table: "imageEditing"),
                        value: Binding(
                            get: { vm.params.dehaze },
                            set: { vm.params.dehaze = $0; vm.syncDiveEditorSlidersToParams() }
                        ),
                        range: 0...100,
                        disabled: !vm.diveEditorSlidersEnabled
                    )
                    diveSlider(
                        localizationService.localizedString("diveEditorClarity", table: "imageEditing"),
                        value: Binding(
                            get: { vm.params.clarity },
                            set: { vm.params.clarity = $0; vm.syncDiveEditorSlidersToParams() }
                        ),
                        range: 0...100,
                        disabled: !vm.diveEditorSlidersEnabled
                    )
                    diveSlider(
                        localizationService.localizedString("diveEditorTemperature", table: "imageEditing"),
                        value: Binding(
                            get: { vm.params.temperature },
                            set: { vm.params.temperature = $0; vm.syncDiveEditorSlidersToParams() }
                        ),
                        range: -100...100,
                        disabled: !vm.diveEditorSlidersEnabled
                    )
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
            .frame(maxHeight: 200)

            HStack(spacing: 12) {
                Button(localizationService.localizedString("cancel", table: "common")) {
                    vm.cancelDiveEditorProcessing()
                    onDismiss()
                }
                .buttonStyle(.bordered)

                Button(localizationService.localizedString("save", table: "common")) {
                    DiveEditorAnalyticsService.shared.track(.savePressed)
                    showSaveMenu = true
                }
                .buttonStyle(.borderedProminent)
                .tint(.divePrimary)

                Button {
                    guard let out = vm.previewImage ?? vm.originalImage else { return }
                    DiveEditorAnalyticsService.shared.track(.sharePressed)
                    shareItems = [out]
                    showShare = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .background(Color.diveCard)
        }
        .background(Color.diveBackground)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button {
                    vm.cancelDiveEditorProcessing()
                    onDismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.diveTextSecondary)
                }
            }
            ToolbarItem(placement: .principal) {
                if vm.isProcessing {
                    HStack(spacing: 8) {
                        ProgressView()
                        Button(localizationService.localizedString("cancel", table: "common")) {
                            vm.cancelDiveEditorProcessing()
                        }
                        .font(.caption)
                    }
                }
            }
        }
        .onAppear {
            vm.isDiveEditorMode = true
            vm.setImage(image)
        }
        .onDisappear {
            manualSliderDebounce?.cancel()
            vm.cancelDiveEditorProcessing()
        }
        .confirmationDialog(localizationService.localizedString("diveEditorSaveTitle", table: "imageEditing"), isPresented: $showSaveMenu) {
            Button(localizationService.localizedString("diveEditorSaveToApp", table: "imageEditing")) {
                saveToAppGallery()
            }
            Button(localizationService.localizedString("diveEditorSaveToPhotos", table: "imageEditing")) {
                saveToPhotoLibrary()
            }
            Button(localizationService.localizedString("diveEditorReplaceOriginal", table: "imageEditing"), role: .destructive) {
                showReplaceConfirm = true
            }
            Button(localizationService.localizedString("cancel", table: "common"), role: .cancel) {}
        }
        .alert(localizationService.localizedString("diveEditorReplaceConfirm", table: "imageEditing"), isPresented: $showReplaceConfirm) {
            Button(localizationService.localizedString("cancel", table: "common"), role: .cancel) {}
            Button(localizationService.localizedString("diveEditorReplaceDestructive", table: "imageEditing"), role: .destructive) {
                saveToPhotoLibrary()
            }
        } message: {
            Text(localizationService.localizedString("diveEditorReplaceFootnote", table: "imageEditing"))
        }
        .sheet(isPresented: $showShare) {
            ShareSheet(items: shareItems)
        }
    }

    @ViewBuilder
    private var compareArea: some View {
        let orig = vm.originalImage
        let after = vm.previewImage ?? vm.originalImage
        GeometryReader { geo in
            ZStack {
                Color.black.opacity(0.08)
                if vm.diveEditorCompare == .split, let o = orig, let a = after {
                    ZStack(alignment: .leading) {
                        Image(uiImage: a)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: geo.size.width, height: geo.size.height)
                        Image(uiImage: o)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .mask(
                                HStack(spacing: 0) {
                                    Rectangle()
                                        .frame(width: max(8, geo.size.width * vm.splitDragRatio))
                                    Spacer(minLength: 0)
                                }
                            )
                        Rectangle()
                            .fill(Color.white)
                            .frame(width: 3)
                            .position(x: geo.size.width * vm.splitDragRatio, y: geo.size.height / 2)
                            .shadow(radius: 2)
                    }
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { g in
                                let x = max(0, min(1, g.location.x / geo.size.width))
                                vm.splitDragRatio = x
                            }
                    )
                } else if vm.diveEditorCompare == .before, let o = orig {
                    Image(uiImage: o)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                } else if let a = after {
                    Image(uiImage: a)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    private func diveSlider(_ title: String, value: Binding<Double>, range: ClosedRange<Double>, disabled: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title)
                    .font(.caption)
                    .foregroundColor(disabled ? .diveTextSecondary.opacity(0.45) : .diveTextSecondary)
                Spacer()
                Text("ui_imageediting_value_3".localized)
                    .font(.caption.monospacedDigit())
                    .foregroundColor(disabled ? .diveTextSecondary.opacity(0.45) : .diveTextSecondary)
            }
            Slider(value: value, in: range, step: 1)
                .tint(.divePrimary)
                .disabled(disabled)
                .onChange(of: value.wrappedValue) { _, _ in
                    vm.syncDiveEditorSlidersToParams()
                    trackManualSlider()
                }
        }
    }

    private func trackManualSlider() {
        manualSliderDebounce?.cancel()
        manualSliderDebounce = Task {
            _ = try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                DiveEditorAnalyticsService.shared.track(.manualSliderChanged, processingMode: "local")
            }
        }
    }

    private func exportUIImage() -> UIImage? {
        guard let base = vm.originalImage else { return nil }
        return vm.previewImage ?? base
    }

    private func saveToAppGallery() {
        guard exportUIImage() != nil else { return }
        vm.syncDiveEditorSlidersToParams()
        vm.exportFullQuality { full in
            DispatchQueue.main.async {
                guard let full else { return }
                _ = try? DiveEditorRecentStore.shared.saveExport(fullImage: full)
            }
        }
    }

    private func saveToPhotoLibrary() {
        guard exportUIImage() != nil else { return }
        vm.syncDiveEditorSlidersToParams()
        vm.exportFullQuality { full in
            DispatchQueue.main.async {
                guard let full, let data = full.jpegData(compressionQuality: 0.92) else { return }
                PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                    guard status == .authorized || status == .limited else { return }
                    PHPhotoLibrary.shared().performChanges {
                        PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
                    } completionHandler: { _, _ in }
                }
            }
        }
    }
}
