//
//  UnderwaterImageProcessor.swift
//  DiveHub
//

import UIKit
import CoreImage

enum UnderwaterWaterType: String, CaseIterable {
    case tropical = "Tropical"
    case green = "Green"
    case murky = "Murky"
    case freshwater = "Freshwater"
}

enum UnderwaterPreset: String, CaseIterable {
    case tropical = "Tropical"
    case greenWater = "Green water"
    case deepDive = "Deep dive (20m)"
    case nightDive = "Night dive (Flash)"
}

struct UnderwaterProcessingParams {
    var temperature: Double = 0
    var tint: Double = 0
    var contrast: Double = 0
    var saturation: Double = 100
    var clarity: Double = 0
    var dehaze: Double = 0
    var backscatter: Double = 0
    var sharpen: Double = 0
    var depthMeters: Double = 10
    var waterType: UnderwaterWaterType = .tropical
}

final class UnderwaterImageProcessor {
    static let shared = UnderwaterImageProcessor()
    private let context = CIContext(options: [.useSoftwareRenderer: false])

    private init() {}

    nonisolated func processPreview(_ image: UIImage, params: UnderwaterProcessingParams, maxSize: CGFloat = 1024) -> UIImage? {
        guard let scaled = image.downscaled(maxSide: maxSize),
              let ci = CIImage(image: scaled) else { return nil }
        return process(ci, params: params, scale: scaled.scale).flatMap { render($0, scale: scaled.scale) }
    }

    nonisolated func processFull(_ image: UIImage, params: UnderwaterProcessingParams) -> UIImage? {
        guard let ci = CIImage(image: image) else { return nil }
        return process(ci, params: params, scale: image.scale).flatMap { render($0, scale: image.scale) }
    }

    nonisolated private func process(_ input: CIImage, params: UnderwaterProcessingParams, scale: CGFloat) -> CIImage? {
        var out = input
        out = applyLiftShadows(out)
        out = applyWhiteBalance(out, temperature: params.temperature, tint: params.tint)
        out = applyColorRestoration(out, depth: params.depthMeters, waterType: params.waterType)
        if params.dehaze > 0 { out = applyDehaze(out, amount: params.dehaze / 100.0) }
        if params.backscatter > 0 { out = applyBackscatterRemoval(out, amount: params.backscatter / 100.0) }
        out = applyColorControls(out, params: params)
        if params.sharpen > 0 { out = applySharpen(out, amount: params.sharpen / 100.0) }
        return out
    }

    nonisolated private func render(_ ci: CIImage, scale: CGFloat) -> UIImage? {
        guard let cg = context.createCGImage(ci, from: ci.extent) else { return nil }
        return UIImage(cgImage: cg, scale: scale, orientation: .up)
    }

    /// Поднятие теней и лёгкое осветление для тёмных подводных кадров.
    nonisolated private func applyLiftShadows(_ input: CIImage) -> CIImage {
        var out = input
        if let shadow = CIFilter(name: "CIHighlightShadowAdjust", parameters: [
            "inputImage": out, "inputShadowAmount": 0.5, "inputHighlightAmount": 0
        ])?.outputImage { out = shadow }
        if let expo = CIFilter(name: "CIColorControls", parameters: [
            "inputImage": out, "inputBrightness": 0.08, "inputContrast": 1.0, "inputSaturation": 1.0
        ])?.outputImage { out = expo }
        return out
    }

    nonisolated private func applyWhiteBalance(_ input: CIImage, temperature: Double, tint: Double) -> CIImage {
        let tr = 1.0 + (temperature / 100.0) * 0.35
        let tb = 1.0 - (temperature / 100.0) * 0.25
        let gr = 1.0 + (tint / 100.0) * 0.08
        let gb = 1.0 - (tint / 100.0) * 0.06
        return colorMatrix(input, r: CGFloat(tr * gr), g: 1, b: CGFloat(tb * gb))
    }

    nonisolated private func colorMatrix(_ input: CIImage, r: CGFloat, g: CGFloat, b: CGFloat) -> CIImage {
        guard let filter = CIFilter(name: "CIColorMatrix", parameters: [
            "inputImage": input,
            "inputRVector": CIVector(x: r, y: 0, z: 0, w: 0),
            "inputGVector": CIVector(x: 0, y: g, z: 0, w: 0),
            "inputBVector": CIVector(x: 0, y: 0, z: b, w: 0),
            "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 1),
            "inputBiasVector": CIVector(x: 0, y: 0, z: 0, w: 0)
        ]) else { return input }
        return filter.outputImage ?? input
    }

    nonisolated private func applyColorRestoration(_ input: CIImage, depth: Double, waterType: UnderwaterWaterType) -> CIImage {
        let r = min(1.0 + (depth / 10.0) * 0.18, 2.8)
        let g = min(1.0 + (depth / 20.0) * 0.08, 1.3)
        let b = max(1.0 - (depth / 30.0) * 0.05, 0.85)
        return colorMatrix(input, r: CGFloat(r), g: CGFloat(g), b: CGFloat(b))
    }

    nonisolated private func applyDehaze(_ input: CIImage, amount: CGFloat) -> CIImage {
        var out = input
        if let f = CIFilter(name: "CIColorControls", parameters: [
            "inputImage": out, "inputContrast": 1.0 + amount * 0.4,
            "inputSaturation": 1.0 + amount * 0.2, "inputBrightness": amount * 0.05
        ]) { out = f.outputImage ?? out }
        if let f = CIFilter(name: "CIHighlightShadowAdjust", parameters: [
            "inputImage": out, "inputShadowAmount": amount * 0.3, "inputHighlightAmount": -amount * 0.1
        ]) { out = f.outputImage ?? out }
        return out
    }

    nonisolated private func applyBackscatterRemoval(_ input: CIImage, amount: CGFloat) -> CIImage {
        // CIMedianFilter on iOS has no inputRadius — only inputImage
        guard let median = CIFilter(name: "CIMedianFilter", parameters: ["inputImage": input])?.outputImage,
              let blend = CIFilter(name: "CIDissolveTransition", parameters: [
                "inputImage": input, "inputTargetImage": median, "inputTime": amount * 0.6
              ])?.outputImage else { return input }
        return blend
    }

    nonisolated private func applyColorControls(_ input: CIImage, params: UnderwaterProcessingParams) -> CIImage {
        let contrast = 1.0 + (params.contrast / 100.0) * 0.5
        let sat = params.saturation / 100.0
        let clarity = 1.0 + (params.clarity / 100.0) * 0.5
        guard let f = CIFilter(name: "CIColorControls", parameters: [
            "inputImage": input, "inputContrast": contrast * clarity, "inputSaturation": sat, "inputBrightness": 0
        ]) else { return input }
        return f.outputImage ?? input
    }

    nonisolated private func applySharpen(_ input: CIImage, amount: CGFloat) -> CIImage {
        guard let f = CIFilter(name: "CISharpenLuminance", parameters: ["inputImage": input, "inputSharpness": amount * 2.0]) else { return input }
        return f.outputImage ?? input
    }
}

extension UIImage {
    nonisolated fileprivate func downscaled(maxSide: CGFloat) -> UIImage? {
        let m = max(size.width, size.height)
        guard m > maxSide else { return self }
        let scale = maxSide / m
        let nw = size.width * scale
        let nh = size.height * scale
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: nw, height: nh))
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: CGSize(width: nw, height: nh))) }
    }
}
