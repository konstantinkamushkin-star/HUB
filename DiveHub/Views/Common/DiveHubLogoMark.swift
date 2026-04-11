import SwiftUI

/// Full-color brand mark from `BrandLogoMask` (JPEG/PNG **without** a shape-only alpha channel).
/// Do not use `.template` here: a fully opaque raster becomes a solid rectangle when tinted.
struct DiveHubLogoMark: View {
    /// Kept for call-site compatibility; raster asset ignores tint.
    var color: Color = .divePrimary

    var body: some View {
        Image("BrandLogoMask")
            .renderingMode(.original)
            .resizable()
            .interpolation(.high)
            .antialiased(true)
            .scaledToFit()
            .accessibilityLabel("DiveHub")
    }
}

/// Small logo for segmented controls / rows. Fixed **square** frame + `scaledToFit` so UISegmentedControl cannot stretch it horizontally.
struct DiveHubBrandIcon: View {
    var size: CGFloat
    /// Ignored for full-color raster; kept for API compatibility with `DiveHubSystemIcon`.
    var color: Color = .divePrimary

    var body: some View {
        Image("BrandLogoMask")
            .renderingMode(.original)
            .resizable()
            .interpolation(.high)
            .antialiased(true)
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel("DiveHub")
    }
}

struct DiveHubSystemIcon: View {
    let name: String
    var color: Color = .divePrimary
    var size: CGFloat = 20

    var body: some View {
        if name == "water.waves" || name == "divehub.logo" {
            DiveHubBrandIcon(size: size, color: color)
        } else {
            Image(systemName: name)
                .foregroundStyle(color)
                .font(.system(size: size * 0.85, weight: .medium))
                .frame(width: size, height: size)
        }
    }
}
