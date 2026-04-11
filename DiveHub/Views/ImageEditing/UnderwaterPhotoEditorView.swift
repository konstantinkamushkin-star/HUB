//
//  UnderwaterPhotoEditorView.swift
//  DiveHub
//

import SwiftUI
import UIKit
import Photos
import PhotosUI
import ImageIO
import UniformTypeIdentifiers

struct UnderwaterPhotoEditorView: View {
    @ObservedObject var viewModel: UnderwaterPhotoEditorViewModel
    var onClose: () -> Void

    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var showExportSheet = false
    @State private var exportedImage: UIImage?

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Color.black.opacity(0.2)
                if viewModel.showSplitView, let orig = viewModel.originalImage, let proc = viewModel.previewImage {
                    HStack(spacing: 0) {
                        Image(uiImage: orig).resizable().aspectRatio(contentMode: .fit)
                            .overlay(Text(LocalizationService.shared.localizedString("before", table: "imageEditing")).font(.caption2).foregroundColor(.white).padding(4), alignment: .topLeading)
                        Rectangle().frame(width: 2).foregroundColor(.white)
                        Image(uiImage: proc).resizable().aspectRatio(contentMode: .fit)
                            .overlay(Text(LocalizationService.shared.localizedString("after", table: "imageEditing")).font(.caption2).foregroundColor(.white).padding(4), alignment: .topLeading)
                    }
                    .scaleEffect(scale).offset(offset)
                } else if let img = viewModel.previewImage ?? viewModel.originalImage {
                    Image(uiImage: img)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .scaleEffect(scale)
                        .offset(offset)
                        .gesture(MagnificationGesture().onChanged { value in scale = value }.onEnded { _ in scale = min(max(scale, 0.5), 4) })
                        .simultaneousGesture(DragGesture().onChanged { value in offset = value.translation })
                }
                if viewModel.isProcessing { ProgressView().scaleEffect(1.2).tint(.white) }
            }
            .frame(maxWidth: .infinity).frame(height: 280).clipped()

            HStack(spacing: 0) {
                ForEach(UnderwaterEditMode.allCases, id: \.self) { mode in
                    Button(action: {
                        viewModel.mode = mode
                        viewModel.updatePreview()
                    }) {
                        Text(mode.rawValue)
                            .font(.caption)
                            .fontWeight(viewModel.mode == mode ? .semibold : .regular)
                            .foregroundColor(viewModel.mode == mode ? .white : .diveTextSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(viewModel.mode == mode ? Color.divePrimary : Color.clear)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 8)

            if let msg = viewModel.aiStatusMessage {
                Text(msg)
                    .font(.caption2)
                    .foregroundColor(.orange)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.15))
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(UnderwaterPreset.allCases, id: \.self) { preset in
                        Button(action: { viewModel.applyPreset(preset) }) {
                            Text(preset.rawValue)
                                .font(.caption2)
                                .lineLimit(1)
                                .foregroundColor(viewModel.selectedPreset == preset ? .white : .diveText)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(viewModel.selectedPreset == preset ? Color.divePrimary : Color.diveCard)
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if viewModel.mode == .manual || viewModel.mode == .auto {
                        sliderRow("Depth (m)", value: Binding(get: { viewModel.depthSliderMeters }, set: { viewModel.setDepth($0) }), range: 0...40, step: 1) { viewModel.updatePreview() }
                        HStack(spacing: 8) {
                            Text(LocalizationService.shared.localizedString("waterType", table: "imageEditing")).font(.caption).foregroundColor(.diveTextSecondary)
                            ForEach(UnderwaterWaterType.allCases, id: \.self) { type in
                                Button(action: { viewModel.setWaterType(type) }) {
                                    Text(type.rawValue).font(.caption2)
                                        .foregroundColor(viewModel.waterType == type ? .white : .diveText)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(viewModel.waterType == type ? Color.divePrimary : Color.diveCard)
                                        .cornerRadius(6)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    sliderRow("Temperature", value: $viewModel.params.temperature, range: -100...100) { viewModel.updatePreview() }
                    sliderRow("Tint", value: $viewModel.params.tint, range: -100...100) { viewModel.updatePreview() }
                    sliderRow("Contrast", value: $viewModel.params.contrast, range: -100...100) { viewModel.updatePreview() }
                    sliderRow("Saturation", value: $viewModel.params.saturation, range: 0...200) { viewModel.updatePreview() }
                    sliderRow("Clarity", value: $viewModel.params.clarity, range: 0...100) { viewModel.updatePreview() }
                    sliderRow("Dehaze", value: $viewModel.params.dehaze, range: 0...100) { viewModel.updatePreview() }
                    sliderRow("Backscatter", value: $viewModel.params.backscatter, range: 0...100) { viewModel.updatePreview() }
                    sliderRow("Sharpen", value: $viewModel.params.sharpen, range: 0...100) { viewModel.updatePreview() }
                }
                .padding(16)
            }
            .frame(maxHeight: 220)

            HStack(spacing: 16) {
                Button(action: { viewModel.showSplitView.toggle() }) {
                    Image(systemName: viewModel.showSplitView ? "rectangle.split.2x2.fill" : "rectangle.split.2x2")
                        .font(.title3).foregroundColor(.divePrimary)
                }
                Button(action: { viewModel.reset() }) {
                    Image(systemName: "arrow.counterclockwise").font(.title3).foregroundColor(.diveTextSecondary)
                }
                Spacer()
                Button(action: exportTapped) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.down")
                        Text(LocalizationService.shared.localizedString("export", table: "imageEditing")).fontWeight(.medium)
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.divePrimary)
                    .cornerRadius(10)
                }
                .disabled(viewModel.isProcessing)
            }
            .padding(.horizontal, 16).padding(.vertical, 12).background(Color.diveCard)
        }
        .background(Color.diveBackground)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill").foregroundColor(.diveTextSecondary)
                }
            }
        }
        .sheet(isPresented: $showExportSheet) {
            if let img = exportedImage {
                SavePhotoSheet(image: img, onDismiss: { showExportSheet = false })
            }
        }
    }

    private func sliderRow(_ label: String, value: Binding<Double>, range: ClosedRange<Double>, step: Double = 1, onChange: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label).font(.caption).foregroundColor(.diveTextSecondary)
                Spacer()
                Text("\(Int(value.wrappedValue))").font(.caption2).foregroundColor(.diveText)
            }
            Slider(value: value, in: range, step: step).tint(.divePrimary).onChange(of: value.wrappedValue) { _, _ in onChange() }
        }
    }

    private func exportTapped() {
        viewModel.exportFullQuality { img in
            exportedImage = img
            showExportSheet = true
        }
    }
}

struct SavePhotoSheet: View {
    let image: UIImage
    var onDismiss: () -> Void
    @Environment(\.dismiss) var envDismiss
    @State private var format: ExportFormat = .heic
    @State private var quality: Double = 0.9
    @State private var saved = false

    enum ExportFormat: String, CaseIterable {
        case jpg = "JPG"
        case heic = "HEIC"
        case png = "PNG"
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Format") {
                    Picker("Format", selection: $format) {
                        ForEach(ExportFormat.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                if format == .jpg || format == .heic {
                    Section("Quality") {
                        Slider(value: $quality, in: 0.8...1.0, step: 0.05)
                        Text("\(Int(quality * 100))%").font(.caption).foregroundColor(.secondary)
                    }
                }
                Section {
                    Button(action: saveToPhotos) {
                        HStack {
                            Image(systemName: "photo.on.rectangle.angled")
                            Text(saved ? "Saved" : "Save to Photos")
                        }
                    }
                    .disabled(saved)
                }
            }
            .navigationTitle(LocalizationService.shared.localizedString("exportTitle", table: "imageEditing"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onDismiss()
                        envDismiss()
                    }
                }
            }
        }
    }

    private func saveToPhotos() {
        let data: Data?
        switch format {
        case .jpg: data = image.jpegData(compressionQuality: quality)
        case .heic: data = image.heicData(compressionQuality: quality)
        case .png: data = image.pngData()
        }
        guard let d = data else { return }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { return }
            PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: d, options: nil)
            } completionHandler: { _, _ in
                DispatchQueue.main.async { saved = true }
            }
        }
    }
}

extension UIImage {
    fileprivate func heicData(compressionQuality: CGFloat) -> Data? {
        guard let cg = cgImage else { return nil }
        let data = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(data as CFMutableData, UTType.heic.identifier as CFString, 1, nil) else { return nil }
        let options: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: compressionQuality]
        CGImageDestinationAddImage(dest, cg, options as CFDictionary)
        guard CGImageDestinationFinalize(dest) else { return nil }
        return data as Data
    }
}

#Preview {
    UnderwaterPhotoEditorView(viewModel: UnderwaterPhotoEditorViewModel(), onClose: {})
}
