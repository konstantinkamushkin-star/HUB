//
//  DiveEditorLightroomAutoEstimator.swift
//  DiveHub — эвристика «Auto» по анализу кадра (как в Lightroom: сдвиг слайдеров от гистограммы/каста).
//

import UIKit

enum DiveEditorLightroomAutoEstimator {
    struct Result: Sendable {
        var depth: Double
        var colorStrength: Double
        var dehaze: Double
        var clarity: Double
        var temperature: Double

        static let fallback = Result(depth: 32, colorStrength: 62, dehaze: 48, clarity: 40, temperature: 12)
    }

    /// Оценка положений слайдеров Dive Editor по содержимому изображения.
    static func estimate(from image: UIImage) -> Result {
        guard let stats = ThumbnailRGBAnalyzer.stats(from: image, maxSide: 96) else {
            return .fallback
        }

        let r = stats.meanR
        let g = stats.meanG
        let b = stats.meanB
        let meanL = stats.meanLuma
        let contrast = min(1, max(0, stats.lumaStd / 0.2))

        // Синий/зелёный каст под водой → теплее (temperature вверх)
        let blueExcess = b - r
        let greenShift = g - (r + b) * 0.5
        let temperature = min(100, max(-100, blueExcess * 95 + greenShift * 35))

        // Темнее кадр + синеватость → выше «глубина» слайдера
        let depth = min(100, max(6, 14 + (1 - meanL) * 58 + max(0, blueExcess) * 42))

        // Низкая насыщённость среднего цвета → сильнее «сила цвета»
        let chroma = max(r, max(g, b)) - min(r, min(g, b))
        let colorStrength = min(100, max(22, 45 + (0.32 - chroma) * 95))

        // Низкий контраст / «молоко» → dehaze и clarity
        let flatness = 1 - contrast
        let dehaze = min(100, max(10, 22 + flatness * 62 + meanL * 0.22 * 35))
        let clarity = min(100, max(6, 18 + flatness * 68))

        return Result(
            depth: depth,
            colorStrength: colorStrength,
            dehaze: dehaze,
            clarity: clarity,
            temperature: temperature
        )
    }
}

private enum ThumbnailRGBAnalyzer {
    struct Stats {
        let meanR: Double
        let meanG: Double
        let meanB: Double
        let meanLuma: Double
        let lumaStd: Double
    }

    static func stats(from image: UIImage, maxSide: CGFloat) -> Stats? {
        let oriented = image.diveEditor_normalizedOrientation()
        let w = oriented.size.width
        let h = oriented.size.height
        guard w > 1, h > 1 else { return nil }

        let scale = min(maxSide / w, maxSide / h, 1)
        let tw = max(1, Int(w * scale))
        let th = max(1, Int(h * scale))

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: tw, height: th), format: format)
        let thumb = renderer.image { _ in
            oriented.draw(in: CGRect(x: 0, y: 0, width: tw, height: th))
        }
        guard let cg = thumb.cgImage else { return nil }

        var buffer = [UInt8](repeating: 0, count: tw * th * 4)
        let ok = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let base = raw.baseAddress else { return false }
            let cs = CGColorSpaceCreateDeviceRGB()
            guard let ctx = CGContext(
                data: base,
                width: tw,
                height: th,
                bitsPerComponent: 8,
                bytesPerRow: tw * 4,
                space: cs,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: tw, height: th))
            return true
        }
        guard ok else { return nil }

        var sumR = 0.0, sumG = 0.0, sumB = 0.0, sumL = 0.0, sumL2 = 0.0
        var count = 0.0

        for i in 0 ..< (tw * th) {
            let o = i * 4
            let a = Double(buffer[o + 3]) / 255
            if a < 0.02 { continue }
            let rp = min(1, Double(buffer[o]) / 255 / a)
            let gp = min(1, Double(buffer[o + 1]) / 255 / a)
            let bp = min(1, Double(buffer[o + 2]) / 255 / a)
            sumR += rp
            sumG += gp
            sumB += bp
            let L = 0.2126 * rp + 0.7152 * gp + 0.0722 * bp
            sumL += L
            sumL2 += L * L
            count += 1
        }

        guard count > 8 else { return nil }

        let meanR = sumR / count
        let meanG = sumG / count
        let meanB = sumB / count
        let meanL = sumL / count
        let variance = max(0, sumL2 / count - meanL * meanL)
        let lumaStd = sqrt(variance)

        return Stats(meanR: meanR, meanG: meanG, meanB: meanB, meanLuma: meanL, lumaStd: lumaStd)
    }
}

private extension UIImage {
    func diveEditor_normalizedOrientation() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let r = UIGraphicsImageRenderer(size: size, format: format)
        return r.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
