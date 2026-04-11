//
//  GoogleSignInBrandButtonLabel.swift
//  DiveHub — цветной логотип Google на прозрачном фоне, тонкая обводка для контраста.
//

import SwiftUI

struct GoogleSignInBrandButtonLabel: View {
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image("GoogleGLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 20, height: 20)
                .accessibilityHidden(true)
            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 50)
        .background(Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.primary.opacity(0.2), lineWidth: 1)
        )
    }
}
