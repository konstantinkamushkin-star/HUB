//
//  InstructorDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InstructorDetailView: View {
    let instructor: Instructor
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showBooking = false
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Header
                HStack(spacing: 16) {
                    AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.avatarURL) ?? "")) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Image(systemName: "person.circle.fill")
                            .foregroundColor(.secondary)
                    }
                    .frame(width: 100, height: 100)
                    .clipShape(Circle())
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text(instructor.name)
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        HStack {
                            Image(systemName: "star.fill")
                                .foregroundColor(.yellow)
                            Text(String(format: "%.1f", instructor.averageRating))
                                .fontWeight(.semibold)
                            Text("(\(instructor.reviewCount) \(localizationService.localizedString("reviews", table: "common")))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Spacer()
                }
                .padding()
                
                Divider()
                
                // Certifications
                if !instructor.certifications.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ui_detail_certifications".localized)
                            .font(.headline)
                        
                        ForEach(instructor.certifications, id: \.self) { cert in
                            HStack {
                                Image(systemName: "checkmark.seal.fill")
                                    .foregroundColor(.green)
                                Text(cert)
                            }
                        }
                    }
                    .padding()
                }
                
                // Languages
                if !instructor.languages.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ui_detail_languages".localized)
                            .font(.headline)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(instructor.languages, id: \.self) { language in
                                    Text(language)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.divePrimary.opacity(0.1))
                                        .foregroundColor(.divePrimary)
                                        .cornerRadius(16)
                                }
                            }
                        }
                    }
                    .padding()
                }
                
                // Bio
                if let bio = instructor.bio, !bio.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ui_detail_about".localized)
                            .font(.headline)
                        Text(bio)
                            .font(.body)
                    }
                    .padding()
                }
                
                // AI Summary
                if let aiSummary = instructor.aiSummary {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Image(systemName: "sparkles")
                                .foregroundColor(.divePrimary)
                            Text("ui_detail_ai_summary".localized)
                                .font(.headline)
                        }
                        Text(aiSummary)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .background(Color.diveBackground)
                    .cornerRadius(12)
                    .padding(.horizontal)
                }
                
                // Reviews
                // Try using instructor.id first, fallback to userId if needed
                ReviewsSection(reviewableType: .instructor, reviewableId: instructor.id)
            }
        }
        .navigationTitle(instructor.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showBooking = true }) {
                    Text("ui_detail_book".localized)
                        .fontWeight(.semibold)
                }
            }
        }
        .sheet(isPresented: $showBooking) {
            BookingWizardView(instructorId: instructor.id)
        }
    }
}

#Preview {
    NavigationView {
        InstructorDetailView(instructor: Instructor(
            id: "1",
            userId: "user1",
            name: "John Doe",
            avatarURL: nil,
            certifications: ["PADI Instructor", "SSI Master"],
            languages: ["English", "Spanish"],
            bio: "Experienced diving instructor with 10+ years of experience",
            trainingSystems: ["PADI", "SSI"],
            credentials: [],
            averageRating: 4.8,
            reviewCount: 45,
            aiSummary: nil,
            schedule: nil
        ))
    }
}
