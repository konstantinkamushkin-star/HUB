import AVFoundation
import SwiftUI
import AVKit
import Photos

struct DiveEditorVideoEditorView: View {
    /// Видео: `ai2` — другой слот UVM (яркость/красный/CLAHE), быстрее Sea-Thru; плюс `luma_boost`/`max_side` на сервере.
    private static let underwaterVideoEngine = "ai2"

    let videoURL: URL
    var onDismiss: () -> Void

    @StateObject private var localizationService = LocalizationService.shared
    @State private var compareMode: DiveEditorCompareMode = .after
    @State private var isProcessing = false
    @State private var errorMessage: String?
    @State private var processedVideoURL: URL?
    @State private var shareItems: [Any] = []
    @State private var showShare = false

    @State private var strength: Double = 0.7
    @State private var useDepth = true
    @State private var depthMeters: Double = 10
    @State private var videoProgress: VideoUnderwaterProcessingProgress?

    var body: some View {
        VStack(spacing: 0) {
            compareArea
                .frame(maxHeight: .infinity)

            if isProcessing, let vp = videoProgress {
                VideoUnderwaterProgressBanner(progress: vp)
                    .padding(.horizontal, 8)
                    .padding(.bottom, 4)
            }

            if let err = errorMessage {
                Text(err)
                    .font(.caption2)
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
            }

            Picker("", selection: $compareMode) {
                Text(localizationService.localizedString("diveEditorCompareAfter", table: "imageEditing")).tag(DiveEditorCompareMode.after)
                Text(localizationService.localizedString("diveEditorCompareBefore", table: "imageEditing")).tag(DiveEditorCompareMode.before)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

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
                Toggle(localizationService.localizedString("depthHintUse", table: "imageEditing"), isOn: $useDepth)
                if useDepth {
                    Stepper(value: $depthMeters, in: 0...60, step: 1) {
                        Text("\(Int(depthMeters)) m")
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            Button {
                Task { await runAutomaticVideoProcessing() }
            } label: {
                actionLabel(localizationService.localizedString("diveEditorAutomaticProcessing", table: "imageEditing"), color: .divePrimary)
            }
            .disabled(isProcessing)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            HStack(spacing: 12) {
                Button(localizationService.localizedString("cancel", table: "common")) { onDismiss() }
                    .buttonStyle(.bordered)

                Button(localizationService.localizedString("save", table: "common")) {
                    guard let out = processedVideoURL else { return }
                    UISaveVideoAtPathToSavedPhotosAlbum(out.path, nil, nil, nil)
                }
                .buttonStyle(.borderedProminent)
                .tint(.divePrimary)
                .disabled(processedVideoURL == nil)

                Button {
                    guard let out = processedVideoURL else { return }
                    shareItems = [out]
                    showShare = true
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .buttonStyle(.bordered)
                .disabled(processedVideoURL == nil)
            }
            .padding()
            .background(Color.diveCard)
        }
        .background(Color.diveBackground)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill").foregroundColor(.diveTextSecondary)
                }
            }
        }
        .sheet(isPresented: $showShare) {
            ShareSheet(items: shareItems)
        }
    }

    private var compareArea: some View {
        let shownURL = (compareMode == .before || processedVideoURL == nil) ? videoURL : (processedVideoURL ?? videoURL)
        return ZStack {
            Color.black.opacity(0.08)
            VideoPlayer(player: AVPlayer(url: shownURL))
        }
    }

    private func actionLabel(_ title: String, color: Color) -> some View {
        HStack {
            if isProcessing { ProgressView().tint(.white) }
            Text(title).fontWeight(.semibold).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(color)
        .foregroundColor(.white)
        .cornerRadius(12)
    }

    private func sourceVideoDurationSeconds() async -> TimeInterval? {
        let asset = AVURLAsset(url: videoURL)
        do {
            let d = try await asset.load(.duration)
            let s = CMTimeGetSeconds(d)
            return (s.isFinite && !s.isNaN && s > 0) ? s : nil
        } catch {
            return nil
        }
    }

    private func runAutomaticVideoProcessing() async {
        do {
            await MainActor.run {
                isProcessing = true
                errorMessage = nil
                videoProgress = VideoUnderwaterProcessingProgress(fraction01: 0, estimatedSecondsRemaining: 0)
            }
            let duration = await sourceVideoDurationSeconds()
            let data = try Data(contentsOf: videoURL)
            let out = try await NetworkService.shared.processVideoUnderwaterVisionModule(
                videoData: data,
                engine: Self.underwaterVideoEngine,
                strength: strength,
                depthHintMeters: useDepth ? depthMeters : nil,
                sourceVideoDuration: duration,
                progress: { p in
                    videoProgress = p
                }
            )
            let outURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("dive_editor_out_\(UUID().uuidString).mp4")
            try out.write(to: outURL, options: .atomic)
            await MainActor.run {
                videoProgress = nil
                processedVideoURL = outURL
                isProcessing = false
            }
        } catch {
            await MainActor.run {
                videoProgress = nil
                isProcessing = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
