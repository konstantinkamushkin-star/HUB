//
//  UnderwaterPhotoEditorViewModel.swift
//  DiveHub
//

import SwiftUI
import UIKit
import Combine

enum UnderwaterEditMode: String, CaseIterable {
    case auto = "AUTO"
    case manual = "MANUAL"
}

enum DiveEditorCompareMode: String, CaseIterable {
    case after = "AFTER"
    case before = "BEFORE"
    case split = "SPLIT"
}

final class UnderwaterPhotoEditorViewModel: ObservableObject {
    @Published var originalImage: UIImage?
    @Published var previewImage: UIImage?
    @Published var isProcessing = false
    @Published var params = UnderwaterProcessingParams()
    @Published var mode: UnderwaterEditMode = .manual
    @Published var selectedPreset: UnderwaterPreset?
    @Published var showSplitView = false
    @Published var depthSliderMeters: Double = 10
    @Published var waterType: UnderwaterWaterType = .tropical
    /// Сообщение при недоступности AI (например "AI сервер недоступен").
    @Published var aiStatusMessage: String?

    // MARK: - Dive Editor (слайдеры → params для облака; локальный UnderwaterImageProcessor не используется)
    var isDiveEditorMode = false
    @Published var diveEditorDepth: Double = 30
    @Published var diveEditorColorStrength: Double = 70
    @Published var diveEditorCompare: DiveEditorCompareMode = .after
    @Published var diveEditorLongWaitMessage: String?
    @Published var splitDragRatio: CGFloat = 0.5

    private let processor = UnderwaterImageProcessor.shared
    private var previewTask: Task<Void, Never>?
    private var diveEditorCloudTask: Task<Void, Never>?
    private var diveEditorAutoDelayTask: Task<Void, Never>?
    private var longWaitTask: Task<Void, Never>?

    private static let diveEditorManualNeutralDepth: Double = 25
    private static let diveEditorManualNeutralColor: Double = 50
    private static let diveEditorManualNeutralDehaze: Double = 0
    private static let diveEditorManualNeutralClarity: Double = 0
    private static let diveEditorManualNeutralTemperature: Double = 0

    /// Глубина для `depth_hint_m` (0…60 м), как на бэкенде: `(depthSlider/100)*40`.
    private static func diveEditorDepthHintMeters(depthSlider: Double) -> Double {
        max(0, min(60, (depthSlider / 100) * 40))
    }

    /// Слайдеры активны в реальном времени; отключаем только на время **облачной** автоматической обработки.
    var diveEditorSlidersEnabled: Bool {
        isDiveEditorMode && !isProcessing
    }

    var hasImage: Bool { originalImage != nil }

    func setImage(_ image: UIImage?) {
        originalImage = image
        if image != nil {
            if isDiveEditorMode {
                previewImage = nil
                applyDiveEditorManualNeutralSliders()
            } else {
                params.depthMeters = depthSliderMeters
                params.waterType = waterType
                if let p = selectedPreset {
                    applyPreset(p)
                } else {
                    updatePreview()
                }
            }
        } else {
            previewImage = nil
        }
    }

    func syncDiveEditorSlidersToParams() {
        params.depthMeters = diveEditorDepth * 40 / 100
        params.saturation = 50 + diveEditorColorStrength
    }

    func applyDiveEditorManualNeutralSliders() {
        diveEditorDepth = Self.diveEditorManualNeutralDepth
        diveEditorColorStrength = Self.diveEditorManualNeutralColor
        params.dehaze = Self.diveEditorManualNeutralDehaze
        params.clarity = Self.diveEditorManualNeutralClarity
        params.temperature = Self.diveEditorManualNeutralTemperature
        syncDiveEditorSlidersToParams()
    }

    func cancelDiveEditorProcessing() {
        diveEditorCloudTask?.cancel()
        diveEditorAutoDelayTask?.cancel()
        diveEditorAutoDelayTask = nil
        longWaitTask?.cancel()
        isProcessing = false
    }

    /// Как «Auto» в Lightroom: по кадру выставляем слайдеры (с анимацией), затем облако с этими значениями.
    func runDiveEditorAutomaticProcessing() {
        guard let img = originalImage else { return }
        diveEditorCloudTask?.cancel()
        diveEditorAutoDelayTask?.cancel()
        longWaitTask?.cancel()
        previewImage = nil
        isProcessing = true
        let auto = DiveEditorLightroomAutoEstimator.estimate(from: img)
        withAnimation(.easeInOut(duration: 0.42)) {
            diveEditorDepth = auto.depth
            diveEditorColorStrength = auto.colorStrength
            params.dehaze = auto.dehaze
            params.clarity = auto.clarity
            params.temperature = auto.temperature
            syncDiveEditorSlidersToParams()
        }
        diveEditorAutoDelayTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 440_000_000)
            guard let self, !Task.isCancelled else { return }
            self.performDiveEditorCloudProcessing()
        }
    }

    private func performDiveEditorCloudProcessing() {
        diveEditorLongWaitMessage = nil
        syncDiveEditorSlidersToParams()
        previewImage = nil

        diveEditorCloudTask?.cancel()
        diveEditorCloudTask = Task { [weak self] in
            guard let self else { return }
            let img: UIImage? = await MainActor.run { [weak self] in self?.originalImage }
            guard let img else {
                await MainActor.run { [weak self] in self?.isProcessing = false }
                return
            }
            let t0 = Date()
            await MainActor.run { [weak self] in
                guard let self else { return }
                self.startDiveEditorLongWaitWatch()
                self.isProcessing = true
                self.aiStatusMessage = nil
            }
            let jpeg = await Task.detached(priority: .userInitiated) {
                Self.jpegDataForCloud(from: img, maxSide: 8192)
            }.value
            guard let jpeg, !Task.isCancelled else {
                await MainActor.run { [weak self] in self?.isProcessing = false }
                return
            }
            let sliders = await MainActor.run { [weak self] in
                guard let self else {
                    return (
                        depth: 30.0, color: 70.0, dehaze: 50.0, clarity: 40.0, temperature: 10.0
                    )
                }
                return (
                    depth: self.diveEditorDepth,
                    color: self.diveEditorColorStrength,
                    dehaze: self.params.dehaze,
                    clarity: self.params.clarity,
                    temperature: self.params.temperature
                )
            }
            let depthHint = Self.diveEditorDepthHintMeters(depthSlider: sliders.depth)
            do {
                // Nikolaj Bech upstream = полное применение матрицы (без «смешивания» из слайдеров).
                let out = try await NetworkService.shared.processPhotoUnderwaterVisionModule(
                    imageJPEG: jpeg,
                    engine: "cursor",
                    strength: 1.0,
                    depthHintMeters: depthHint
                )
                guard !Task.isCancelled else { return }
                guard let ui = UIImage(data: out) else { throw NetworkError.decodingError }
                let ms = Int(Date().timeIntervalSince(t0) * 1000)
                await MainActor.run { [weak self] in
                    guard let self else { return }
                    self.previewImage = ui
                    self.isProcessing = false
                    self.diveEditorLongWaitMessage = nil
                    self.longWaitTask?.cancel()
                    DiveEditorAnalyticsService.shared.track(
                        .autoAiCompleted,
                        processingMode: "cloud_uvm_bech_automatic",
                        success: true,
                        durationMs: ms
                    )
                }
            } catch {
                do {
                    let imageId = try await NetworkService.shared.uploadImageForProcessing(jpegData: jpeg)
                    let payload = NetworkService.ImageProcessParamsPayload(
                        depth: sliders.depth,
                        strength: sliders.color,
                        dehaze: sliders.dehaze,
                        clarity: sliders.clarity,
                        temperature: sliders.temperature,
                        auto_ai: false,
                        pipeline: "default"
                    )
                    let job = try await NetworkService.shared.createImageProcessJob(imageId: imageId, params: payload)
                    let out = try await NetworkService.shared.waitForImageProcessJob(
                        jobId: job.job_id,
                        maxWaitSeconds: 120
                    )
                    guard !Task.isCancelled else { return }
                    guard let ui = UIImage(data: out) else { throw NetworkError.decodingError }
                    let ms = Int(Date().timeIntervalSince(t0) * 1000)
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        self.previewImage = ui
                        self.isProcessing = false
                        self.diveEditorLongWaitMessage = nil
                        self.longWaitTask?.cancel()
                        DiveEditorAnalyticsService.shared.track(
                            .autoAiCompleted,
                            processingMode: "cloud_job_automatic",
                            success: true,
                            durationMs: ms
                        )
                    }
                } catch {
                    await runDiveEditorArticleFallback(jpeg: jpeg, startedAt: t0)
                }
            }
        }
    }

    private func runDiveEditorArticleFallback(jpeg: Data, startedAt: Date) async {
        guard !Task.isCancelled else { return }
        do {
            let depthM = await MainActor.run { [weak self] in
                (self?.diveEditorDepth ?? 30) * 40 / 100
            }
            let strength = await MainActor.run { [weak self] in
                guard let self else { return 0.7 }
                return max(0.3, min(1, (self.params.dehaze + self.diveEditorColorStrength) / 130))
            }
            let out = try await NetworkService.shared.processUnderwaterPhotoWithAI(
                imageData: jpeg,
                depthMeters: depthM,
                strength: strength,
                useAi: false,
                pipeline: "default"
            )
            guard !Task.isCancelled else { return }
            guard let ui = UIImage(data: out) else { throw NetworkError.decodingError }
            let ms = Int(Date().timeIntervalSince(startedAt) * 1000)
            await MainActor.run { [weak self] in
                guard let self else { return }
                self.previewImage = ui
                self.isProcessing = false
                self.diveEditorLongWaitMessage = nil
                self.longWaitTask?.cancel()
                self.aiStatusMessage = nil
                DiveEditorAnalyticsService.shared.track(
                    .autoAiCompleted,
                    processingMode: "article_sync_automatic",
                    success: true,
                    durationMs: ms
                )
            }
        } catch {
            let ms = Int(Date().timeIntervalSince(startedAt) * 1000)
            await MainActor.run { [weak self] in
                guard let self else { return }
                self.isProcessing = false
                self.diveEditorLongWaitMessage = nil
                self.longWaitTask?.cancel()
                self.aiStatusMessage = String(describing: error)
                DiveEditorAnalyticsService.shared.track(
                    .processingFailed,
                    processingMode: "dive_editor_automatic",
                    success: false,
                    durationMs: ms,
                    extra: ["error": error.localizedDescription]
                )
                DiveEditorAnalyticsService.shared.track(
                    .autoAiCompleted,
                    processingMode: "local_only",
                    success: false,
                    durationMs: ms
                )
                self.syncDiveEditorSlidersToParams()
            }
        }
    }

    private func startDiveEditorLongWaitWatch() {
        longWaitTask?.cancel()
        longWaitTask = Task {
            try? await Task.sleep(nanoseconds: 25_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run { [weak self] in
                guard let self else { return }
                if self.isProcessing {
                    self.diveEditorLongWaitMessage = LocalizationService.shared.localizedString("diveEditorProcessingSlow", table: "imageEditing")
                }
            }
        }
    }

    nonisolated private static func jpegDataForCloud(from image: UIImage, maxSide: CGFloat) -> Data? {
        let pixelW = image.size.width * image.scale
        let pixelH = image.size.height * image.scale
        let m = max(pixelW, pixelH)
        let toDraw: UIImage
        if m > maxSide {
            let down = maxSide / m
            let nw = max(1, floor(pixelW * down))
            let nh = max(1, floor(pixelH * down))
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: nw, height: nh), format: format)
            toDraw = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: CGSize(width: nw, height: nh)))
            }
        } else {
            toDraw = image
        }
        return toDraw.jpegData(compressionQuality: 0.95)
    }

    func updateParams(_ block: (inout UnderwaterProcessingParams) -> Void) {
        block(&params)
        updatePreview()
    }

    func setDepth(_ meters: Double) {
        depthSliderMeters = meters
        params.depthMeters = meters
        updatePreview()
    }

    func setWaterType(_ type: UnderwaterWaterType) {
        waterType = type
        params.waterType = type
        updatePreview()
    }

    func applyPreset(_ preset: UnderwaterPreset) {
        selectedPreset = preset
        switch preset {
        case .tropical:
            params.depthMeters = 5
            params.waterType = .tropical
            params.temperature = 10
            params.saturation = 115
            params.dehaze = 30
            params.backscatter = 20
            params.sharpen = 15
        case .greenWater:
            params.depthMeters = 8
            params.waterType = .green
            params.temperature = -5
            params.tint = 10
            params.saturation = 120
            params.dehaze = 50
            params.backscatter = 35
            params.sharpen = 20
        case .deepDive:
            params.depthMeters = 20
            params.waterType = .tropical
            params.temperature = 25
            params.saturation = 130
            params.dehaze = 60
            params.backscatter = 45
            params.sharpen = 25
        case .nightDive:
            params.depthMeters = 0
            params.temperature = 5
            params.tint = -10
            params.contrast = 15
            params.saturation = 110
            params.dehaze = 10
            params.backscatter = 50
            params.sharpen = 30
        }
        depthSliderMeters = params.depthMeters
        waterType = params.waterType
        updatePreview()
    }

    func updatePreview() {
        guard !isDiveEditorMode else { return }
        previewTask?.cancel()
        guard let img = originalImage else { return }
        let p = params
        previewTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 80_000_000)
            guard !Task.isCancelled else { return }
            isProcessing = true
            aiStatusMessage = nil
            let result = await Task.detached(priority: .userInitiated) {
                self.processor.processPreview(img, params: p)
            }.value
            guard !Task.isCancelled else { return }
            previewImage = result ?? img
            isProcessing = false
        }
    }

    func exportFullQuality(completion: @escaping (UIImage?) -> Void) {
        guard let img = originalImage else { completion(nil); return }
        if isDiveEditorMode {
            completion(previewImage ?? img)
            return
        }
        isProcessing = true
        Task.detached(priority: .userInitiated) { [processor, params] in
            let result = processor.processFull(img, params: params)
            await MainActor.run {
                self.isProcessing = false
                completion(result)
            }
        }
    }

    func reset() {
        params = UnderwaterProcessingParams()
        params.depthMeters = depthSliderMeters
        params.waterType = waterType
        selectedPreset = nil
        updatePreview()
    }
}
