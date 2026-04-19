//
//  DiveCenterPromoCard.swift
//  DiveHub
//

import SwiftUI
import UIKit

/// Promo hint for dive center owners (replaces generic “Book” entry).
struct DiveCenterPromoCard: View {
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "person.2.fill")
                .font(.title2)
                .foregroundColor(.divePrimary)
                .frame(width: 36, height: 36)
            VStack(alignment: .leading, spacing: 4) {
                Text(localizationService.localizedString("diveCenterPromoTitle", table: "common"))
                    .font(.headline)
                    .fontWeight(.semibold)
                    .foregroundColor(.primary)
                Text(localizationService.localizedString("diveCenterPromoSubtitle", table: "common"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 1)
        )
    }
}

#Preview {
    DiveCenterPromoCard()
        .padding()
}
