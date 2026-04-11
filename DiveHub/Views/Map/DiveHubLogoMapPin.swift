//
//  DiveHubLogoMapPin.swift
//  DiveHub
//
//  MKMarkerAnnotationView glyphs expect small template artwork; a full JPEG often draws as a blank tile.
//  We rasterize the brand image into a fixed-size pin bitmap for MKAnnotationView.
//

import UIKit

enum DiveHubLogoMapPin {
    static let reuseIdentifier = "DiveHubLogoRasterPin"

    private static var cached: UIImage?

    /// Callout-capable pin: logo aspect-fitted on a light disk so it reads on any map style.
    static func pinImage() -> UIImage? {
        if let existing = cached { return existing }
        guard let src = UIImage(named: "BrandLogoMask") else { return nil }
        let canvas: CGFloat = 44
        let diskInset: CGFloat = 3
        let diskRect = CGRect(x: diskInset, y: diskInset, width: canvas - diskInset * 2, height: canvas - diskInset * 2)
        let innerPadding: CGFloat = 5
        let contentRect = diskRect.insetBy(dx: innerPadding, dy: innerPadding)
        let scale = min(contentRect.width / src.size.width, contentRect.height / src.size.height)
        let drawW = src.size.width * scale
        let drawH = src.size.height * scale
        let drawX = contentRect.midX - drawW / 2
        let drawY = contentRect.midY - drawH / 2
        let drawRect = CGRect(x: drawX, y: drawY, width: drawW, height: drawH)

        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.scale = UIScreen.main.scale

        let img = UIGraphicsImageRenderer(size: CGSize(width: canvas, height: canvas), format: format).image { ctx in
            let cg = ctx.cgContext
            cg.setShadow(offset: CGSize(width: 0, height: 1.5), blur: 3, color: UIColor.black.withAlphaComponent(0.28).cgColor)
            UIColor.white.withAlphaComponent(0.95).setFill()
            cg.fillEllipse(in: diskRect)
            cg.setShadow(offset: .zero, blur: 0, color: nil)
            src.draw(in: drawRect)
        }
        cached = img
        return img
    }
}
