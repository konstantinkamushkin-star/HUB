//
//  VideoUnderwaterProgressBanner.swift
//  DiveHub
//

import SwiftUI

struct VideoUnderwaterProgressBanner: View {
    let progress: VideoUnderwaterProcessingProgress

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ProgressView(value: progress.fraction01, total: 1)
                .tint(.divePrimary)
            HStack {
                Text("\(percentString)%")
                    .fontWeight(.medium)
                Spacer()
                if progress.estimatedSecondsRemaining > 0.5, progress.fraction01 < 0.995 {
                    Text(
                        String(
                            format: LocalizationService.shared.localizedString("videoProcessingRemainingAbout", table: "imageEditing"),
                            formatClock(progress.estimatedSecondsRemaining)
                        )
                    )
                }
            }
            .font(.caption.monospacedDigit())
            .foregroundColor(.diveTextSecondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.diveCard.opacity(0.9))
        .cornerRadius(12)
    }

    private var percentString: String {
        let p = Int((progress.fraction01 * 100).rounded(.down))
        return String(min(100, max(0, p)))
    }

    private func formatClock(_ t: TimeInterval) -> String {
        let s = max(0, Int(ceil(t)))
        let m = s / 60
        let r = s % 60
        return String(format: "%d:%02d", m, r)
    }
}
