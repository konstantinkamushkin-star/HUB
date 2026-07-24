//
//  DiveSiteDetailView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct DiveSiteDetailView: View {
    let site: DiveSite
    var onShowOnMap: (() -> Void)? = nil
    @State private var displayedSite: DiveSite
    @State private var contributionMode: DiveSiteContributionMode?
    @StateObject private var localizationService = LocalizationService.shared
    @State private var recentDivePhotos: [String] = []
    @State private var isLoadingDetail = false

    init(site: DiveSite, onShowOnMap: (() -> Void)? = nil) {
        self.site = site
        self.onShowOnMap = onShowOnMap
        _displayedSite = State(initialValue: site)
    }

    /// Extra site photos (often dive maps / diagrams from catalog imports).
    private var diveMapPhotos: [String] {
        Array(displayedSite.photos.dropFirst())
    }

    private var galleryPhotos: [String] {
        var seen = Set<String>()
        var merged: [String] = []
        let cover = displayedSite.photos.prefix(1)
        for raw in cover + recentDivePhotos {
            let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if value.isEmpty { continue }
            if seen.insert(value).inserted {
                merged.append(value)
            }
        }
        return merged
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Photo Gallery
                if !galleryPhotos.isEmpty {
                    TabView {
                        ForEach(galleryPhotos, id: \.self) { photoURL in
                            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: photoURL) ?? "")) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Rectangle()
                                    .fill(Color.gray.opacity(0.3))
                            }
                        }
                    }
                    .frame(height: 250)
                    .tabViewStyle(.page)
                }
                
                VStack(alignment: .leading, spacing: 12) {
                    // Title and Rating
                    HStack {
                        VStack(alignment: .leading) {
                            Text(displayedSite.displayName)
                                .font(.title)
                                .fontWeight(.bold)
                            Text(displayedSite.siteType.displayName)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        VStack(alignment: .trailing) {
                            HStack {
                                Image(systemName: "star.fill")
                                    .foregroundColor(.yellow)
                                Text(String(format: "%.1f", displayedSite.averageRating))
                                    .fontWeight(.semibold)
                            }
                            Text("(\(displayedSite.reviewCount) \(localizationService.localizedString("reviews", table: "common")))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Divider()
                    
                    // Key Info
                    VStack(alignment: .leading, spacing: 8) {
                        DiveSiteInfoRow(icon: "gauge", title: localizationService.localizedString("maxDepth", table: "logbook"), value: "\(Int(displayedSite.maxDepth))m")
                        DiveSiteInfoRow(icon: "chart.bar", title: localizationService.localizedString("avgDepth", table: "logbook"), value: "\(Int(displayedSite.averageDepth))m")
                        DiveSiteInfoRow(icon: "exclamationmark.triangle", title: localizationService.localizedString("difficulty", table: "explore"), value: displayedSite.difficulty.displayName)
                        if let visibility = displayedSite.visibility {
                            DiveSiteInfoRow(icon: "eye", title: localizationService.localizedString("visibility", table: "logbook"), value: visibility)
                        }
                    }
                    
                    // Description
                    if !displayedSite.displayDescription.isEmpty {
                        Text(localizationService.localizedString("description", table: "common"))
                            .font(.headline)
                        Text(displayedSite.displayDescription)
                            .font(.body)
                    }

                    if !diveMapPhotos.isEmpty {
                        Text(localizationService.localizedString("diveMaps", table: "diveSite"))
                            .font(.headline)
                        DiveSiteMediaStrip(urls: diveMapPhotos)
                    }

                    if !displayedSite.videos.isEmpty {
                        Text(localizationService.localizedString("diveVideos", table: "diveSite"))
                            .font(.headline)
                        DiveSiteMediaStrip(urls: displayedSite.videos)
                    }
                    
                    // AI Summary
                    if let aiSummary = displayedSite.aiSummary {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Image(systemName: "sparkles")
                                    .foregroundColor(.divePrimary)
                                Text(localizationService.localizedString("aiSummary", table: "diveSite"))
                                    .font(.headline)
                            }
                            Text(aiSummary)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .padding()
                        .background(Color.diveBackground)
                        .cornerRadius(12)
                    }
                    
                    // Marine Life
                    if !displayedSite.marineLife.isEmpty {
                        Text(localizationService.localizedString("marineLife", table: "diveSite"))
                            .font(.headline)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(displayedSite.marineLife, id: \.self) { life in
                                    Text(life)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.divePrimary.opacity(0.1))
                                        .foregroundColor(.divePrimary)
                                        .cornerRadius(16)
                                }
                            }
                        }
                    }
                    
                    // Recent Dives Section (only if users share their logbook)
                    RecentDivesSection(
                        diveSiteId: displayedSite.id,
                        onPhotosLoaded: { photos in
                            recentDivePhotos = photos
                        }
                    )
                    
                    // Reviews Section
                    ReviewsSection(reviewableType: .diveSite, reviewableId: displayedSite.id)
                }
                .padding()
            }
        }
        .navigationTitle(displayedSite.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: site.id) {
            await loadFullSiteIfNeeded()
        }
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if AuthenticationService.shared.isAuthenticated {
                    Button(action: { contributionMode = .correction(displayedSite) }) {
                        Image(systemName: "exclamationmark.bubble")
                    }
                    .accessibilityLabel(localizationService.localizedString("reportDiveSiteInaccuracy", table: "diveSite"))
                }
                if let onShowOnMap = onShowOnMap {
                    Button(action: onShowOnMap) {
                        Image(systemName: "map")
                    }
                }
            }
        }
        .sheet(item: $contributionMode) { mode in
            DiveSiteContributionSheet(mode: mode)
        }
    }

    private func loadFullSiteIfNeeded() async {
        guard !isLoadingDetail else { return }
        isLoadingDetail = true
        defer { isLoadingDetail = false }
        do {
            let full = try await NetworkService.shared.getDiveSite(id: site.id)
            displayedSite = full
        } catch {
            #if DEBUG
            print("⚠️ [DiveSiteDetailView] Failed to load site \(site.id): \(error)")
            #endif
        }
    }
}

private struct DiveSiteMediaStrip: View {
    let urls: [String]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(urls, id: \.self) { raw in
                    AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: raw) ?? "")) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        default:
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                        }
                    }
                    .frame(width: 220, height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }
}

struct DiveSiteInfoRow: View {
    let icon: String
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.divePrimary)
                .frame(width: 24)
            Text(title)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
    }
}

struct ReviewsSection: View {
    let reviewableType: ReviewableType
    let reviewableId: String
    @StateObject private var viewModel = ReviewsViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var showReviewEditor = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizationService.localizedString("reviews", table: "common"))
                    .font(.headline)
                Spacer()
                if authService.isAuthenticated {
                    Button(action: { showReviewEditor = true }) {
                        Label(
                            viewModel.myReview == nil
                                ? localizationService.localizedString("addReview", table: "common")
                                : localizationService.localizedString("editReview", table: "common"),
                            systemImage: viewModel.myReview == nil ? "plus.circle.fill" : "pencil.circle.fill"
                        )
                        .font(.subheadline)
                    }
                }
            }
            
            if viewModel.reviews.isEmpty {
                Text(localizationService.localizedString("noReviewsYet", table: "diveSite"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            } else {
                ForEach(viewModel.reviews) { review in
                    ReviewRow(
                        review: review,
                        isMine: review.userId == authService.currentUser?.id,
                        onEdit: { showReviewEditor = true }
                    )
                }
            }
        }
        .task {
            await viewModel.loadReviews(type: reviewableType, id: reviewableId)
        }
        .sheet(isPresented: $showReviewEditor) {
            AddReviewView(
                reviewableType: reviewableType,
                reviewableId: reviewableId,
                existing: viewModel.myReview,
                viewModel: viewModel,
                onDismiss: { showReviewEditor = false }
            )
        }
    }
}

struct AddReviewView: View {
    let reviewableType: ReviewableType
    let reviewableId: String
    var existing: Review? = nil
    @ObservedObject var viewModel: ReviewsViewModel
    var onDismiss: () -> Void
    
    @StateObject private var localizationService = LocalizationService.shared
    @State private var rating: Int = 5
    @State private var text: String = ""
    @State private var errorMessage: String?
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    private var isEditing: Bool { existing != nil }
    
    var body: some View {
        NavigationView {
            Form {
                Section {
                    HStack(spacing: 8) {
                        Text(localizationService.localizedString("rating", table: "common"))
                        Spacer()
                        HStack(spacing: 4) {
                            ForEach(1...5, id: \.self) { value in
                                Button(action: { rating = value }) {
                                    Image(systemName: value <= rating ? "star.fill" : "star")
                                        .foregroundColor(value <= rating ? .yellow : .gray)
                                        .font(.title2)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                Section(localizationService.localizedString("yourReview", table: "common")) {
                    TextEditor(text: $text)
                        .frame(minHeight: 100)
                }
                if isEditing {
                    Section {
                        Button(role: .destructive) {
                            confirmDelete = true
                        } label: {
                            Text(localizationService.localizedString("deleteReview", table: "common"))
                        }
                        .disabled(viewModel.isSubmitting)
                    }
                }
                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle(
                localizationService.localizedString(isEditing ? "editReview" : "addReview", table: "common")
            )
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        onDismiss()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizationService.localizedString(isEditing ? "save" : "submit", table: "common")) {
                        Task { await submit() }
                    }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSubmitting)
                }
            }
            .onAppear {
                if let existing {
                    rating = existing.rating
                    text = existing.text
                }
            }
            .onSubmit { Task { await submit() } }
            .confirmationDialog(
                localizationService.localizedString("deleteReviewConfirm", table: "common"),
                isPresented: $confirmDelete,
                titleVisibility: .visible
            ) {
                Button(localizationService.localizedString("deleteReview", table: "common"), role: .destructive) {
                    Task { await deleteMine() }
                }
                Button(localizationService.localizedString("cancel", table: "common"), role: .cancel) {}
            }
        }
    }
    
    private func submit() async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            errorMessage = localizationService.localizedString("reviewTextRequired", table: "diveSite")
            return
        }
        errorMessage = nil
        do {
            if let existing {
                try await viewModel.updateReview(
                    id: existing.id,
                    reviewableType: reviewableType,
                    reviewableId: reviewableId,
                    rating: rating,
                    text: trimmed
                )
            } else {
                try await viewModel.submitReview(
                    reviewableType: reviewableType,
                    reviewableId: reviewableId,
                    rating: rating,
                    text: trimmed
                )
            }
            onDismiss()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deleteMine() async {
        guard let existing else { return }
        errorMessage = nil
        do {
            try await viewModel.deleteReview(
                id: existing.id,
                reviewableType: reviewableType,
                reviewableId: reviewableId
            )
            onDismiss()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct RecentDivesSection: View {
    let diveSiteId: String
    var onPhotosLoaded: (([String]) -> Void)? = nil
    @StateObject private var settingsService = SettingsService.shared
    @State private var recentDives: [DiveLog] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("recentDives", table: "diveSite"))
                .font(.headline)
            
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else if let error = errorMessage {
                Text("\(localizationService.localizedString("errorLoadingDives", table: "diveSite")): \(error)")
                    .font(.caption)
                    .foregroundColor(.red)
            } else if recentDives.isEmpty {
                Text(localizationService.localizedString("noRecentDives", table: "diveSite"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            } else {
                ForEach(recentDives.prefix(5)) { dive in
                    RecentDiveRow(dive: dive)
                }
            }
        }
        .task {
            await loadRecentDives()
        }
    }
    
    private func loadRecentDives() async {
        // Validate authentication state first
        AuthenticationService.shared.validateAuthentication()
        
        // Check if user is authenticated AND has valid tokens - public dives still require auth to see who shared
        let isAuth = AuthenticationService.shared.isAuthenticated
        let hasUser = AuthenticationService.shared.currentUser != nil
        let hasAccessToken = KeychainService.shared.getAccessToken() != nil
        let hasRefreshToken = KeychainService.shared.getRefreshToken() != nil
        let hasValidTokens = hasAccessToken || hasRefreshToken
        
        guard isAuth, hasUser, hasValidTokens else {
            errorMessage = LocalizationService.shared.localizedString("pleaseSignInToViewDives", table: "diveSite")
            isLoading = false
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            recentDives = try await NetworkService.shared.getPublicDiveLogsForSite(diveSiteId: diveSiteId)
            // Sort by date, most recent first
            recentDives.sort { $0.date > $1.date }
            let photos = recentDives
                .flatMap { $0.photos }
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            onPhotosLoaded?(Array(Set(photos)))
        } catch {
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
}

struct RecentDiveRow: View {
    let dive: DiveLog
    @StateObject private var settingsService = SettingsService.shared
    @State private var user: User?
    @State private var isLoadingUser = false
    
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            if let firstPhoto = dive.photos.first,
               let fullURL = NetworkService.shared.fullImageURL(from: firstPhoto),
               let url = URL(string: fullURL) {
                AsyncImage(url: url) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle()
                        .fill(Color.gray.opacity(0.25))
                }
                .frame(width: 72, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    if let user = user {
                        NavigationLink(destination: UserProfileView(userId: user.id)) {
                            HStack(spacing: 4) {
                                if let fullAvatarURL = NetworkService.shared.fullImageURL(from: user.avatarURL), let url = URL(string: fullAvatarURL) {
                                    AsyncImage(url: url) { image in
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Image(systemName: "person.circle.fill")
                                            .foregroundColor(.secondary)
                                    }
                                    .frame(width: 24, height: 24)
                                    .clipShape(Circle())
                                } else {
                                    Image(systemName: "person.circle.fill")
                                        .foregroundColor(.secondary)
                                        .frame(width: 24, height: 24)
                                }
                                Text(user.displayName)
                                    .font(.caption)
                                    .foregroundColor(.divePrimary)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    } else if isLoadingUser {
                        ProgressView()
                            .scaleEffect(0.7)
                    }
                    Spacer()
                }
                Text(dive.date.formatted(date: .abbreviated, time: .omitted))
                    .font(.headline)
                if !dive.notes.isEmpty {
                    Text(dive.notes)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(formatDepth(dive.maxDepth))
                    .font(.headline)
                Text("\(dive.bottomTime) \(LocalizationService.shared.localizedString("min", table: "logbook"))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.diveBackground)
        .cornerRadius(12)
        .task {
            await loadUser()
        }
    }
    
    private func loadUser() async {
        guard user == nil && !isLoadingUser else { return }
        isLoadingUser = true
        do {
            user = try await NetworkService.shared.getUser(userId: dive.userId)
        } catch {
            // Silently fail - user info is optional
        }
        isLoadingUser = false
    }
    
    private func formatDepth(_ meters: Double) -> String {
        if settingsService.measurementUnits.depth == .feet {
            let feet = Int(meters.metersToFeet())
            return "\(feet)ft"
        }
        return "\(Int(meters))m"
    }
}

struct ReviewRow: View {
    let review: Review
    var isMine: Bool = false
    var onEdit: (() -> Void)? = nil
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(review.userName)
                    .fontWeight(.semibold)
                Spacer()
                if isMine, let onEdit {
                    Button(action: onEdit) {
                        Text(localizationService.localizedString("editReview", table: "common"))
                            .font(.caption)
                    }
                }
                HStack {
                    ForEach(1...5, id: \.self) { index in
                        Image(systemName: index <= review.rating ? "star.fill" : "star")
                            .foregroundColor(index <= review.rating ? .yellow : .gray)
                            .font(.caption)
                    }
                }
            }
            Text(review.text)
                .font(.body)
            Text(review.createdAt.formatted())
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color.diveBackground)
        .cornerRadius(12)
    }
}

#Preview {
    NavigationView {
        DiveSiteDetailView(site: DiveSite(
            id: "1",
            name: "Blue Hole",
            description: "A famous dive site",
            location: DiveSite.Location(latitude: 20.0, longitude: -80.0),
            siteType: .reef,
            difficulty: .intermediate,
            maxDepth: 30,
            averageDepth: 20,
            visibility: "15-25m",
            current: "Moderate",
            marineLife: ["Sharks", "Turtles"],
            photos: [],
            videos: [],
            averageRating: 4.5,
            reviewCount: 42,
            aiSummary: nil,
            affiliatedCenters: [],
            createdAt: Date(),
            updatedAt: Date()
        ))
    }
}
