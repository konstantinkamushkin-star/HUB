//
//  FeedView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

struct FeedView: View {
    @StateObject private var viewModel = FeedViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showCreatePost = false
    
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    // Recommendations section
                    if !viewModel.recommendations.isEmpty {
                        RecommendationsSection(recommendations: viewModel.recommendations)
                    }
                    
                    if viewModel.isLoading && viewModel.posts.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding()
                    } else if viewModel.posts.isEmpty {
                        VStack(spacing: 16) {
                            Image(systemName: "newspaper")
                                .font(.system(size: 50))
                                .foregroundColor(.secondary)
                            Text(localizationService.localizedString("noPostsYet", table: "feed"))
                                .font(.headline)
                                .foregroundColor(.secondary)
                            Text(localizationService.localizedString("beFirstToShare", table: "feed"))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                    } else {
                        ForEach(Array(viewModel.posts.enumerated()), id: \.element.id) { index, post in
                            FeedPostRow(post: post, viewModel: viewModel)
                                .onAppear {
                                    if index == viewModel.posts.count - 1 {
                                        Task { await viewModel.loadMoreFeed() }
                                    }
                                }
                        }
                        if viewModel.isLoadingMore {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding()
                        }
                    }
                }
                .padding()
                .padding(.bottom, 36)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(localizationService.localizedString("feed", table: "feed"))
            .diveHubNavigationChrome()
            .refreshable {
                await viewModel.loadFeed()
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showCreatePost = true
                    }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title3)
                    }
                }
            }
            .sheet(isPresented: $showCreatePost) {
                CreatePostView()
                    .onDisappear {
                        Task {
                            await viewModel.loadFeed()
                        }
                    }
            }
            .task {
                await viewModel.loadFeed()
                await viewModel.loadRecommendations()
            }
        }
    }
}

struct FeedPostRow: View {
    let post: FeedPost
    @ObservedObject var viewModel: FeedViewModel
    @State private var showComments = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // User header
            HStack {
                if let user = post.user {
                    NavigationLink(destination: UserProfileView(userId: user.id)) {
                        HStack(spacing: 8) {
                            if let avatarURL = user.avatarURL, let url = URL(string: avatarURL.hasPrefix("http") ? avatarURL : NetworkService.shared.baseURL + avatarURL) {
                                AsyncImage(url: url) { image in
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Image(systemName: "person.circle.fill")
                                        .foregroundColor(.secondary)
                                }
                                .frame(width: 40, height: 40)
                                .clipShape(Circle())
                            } else {
                                Image(systemName: "person.circle.fill")
                                    .foregroundColor(.secondary)
                                    .frame(width: 40, height: 40)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(user.displayName)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                Text(post.createdAt.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                Spacer()
            }
            
            // Post content
            if let content = post.content {
                Text(content)
                    .font(.body)
                    .foregroundStyle(.primary)
            }
            
            // Dive log preview
            if let diveLog = post.diveLog {
                NavigationLink(destination: DiveLogDetailView(log: diveLog)) {
                    DiveLogPostPreview(diveLog: diveLog)
                }
                .buttonStyle(.plain)
            }
            
            // Photos
            if !post.photos.isEmpty {
                TabView {
                    ForEach(post.photos, id: \.self) { photoURL in
                        AsyncImage(url: URL(string: photoURL.hasPrefix("http") ? photoURL : NetworkService.shared.baseURL + photoURL)) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Rectangle()
                                .fill(Color.gray.opacity(0.3))
                        }
                    }
                }
                .frame(height: 300)
                .tabViewStyle(.page)
            }
            
            // Actions
            HStack(spacing: 24) {
                Button(action: {
                    Task {
                        await viewModel.toggleLike(postId: post.id)
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: post.isLiked == true ? "heart.fill" : "heart")
                            .foregroundStyle(post.isLiked == true ? Color.red : Color.primary)
                        Text("\(post.likes)")
                            .font(.caption)
                            .foregroundStyle(.primary)
                    }
                }
                
                Button(action: {
                    showComments = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "bubble.right")
                        Text("\(post.comments)")
                            .font(.caption)
                    }
                    .foregroundStyle(.primary)
                }
                
                Spacer()
            }
        }
        .padding()
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .cornerRadius(12)
        .sheet(isPresented: $showComments) {
            PostCommentsView(postId: post.id)
        }
    }
}

struct DiveLogPostPreview: View {
    let diveLog: DiveLog
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                DiveHubLogoMark(color: .divePrimary)
                    .aspectRatio(1, contentMode: .fit)
                    .frame(width: 22, height: 22)
                Text(localizationService.localizedString("diveLog", table: "feed"))
                    .font(.headline)
                    .foregroundStyle(.primary)
                Spacer()
                Text(diveLog.date.formatted(date: .abbreviated, time: .omitted))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizationService.localizedString("maxDepth", table: "feed"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(formatDepth(diveLog.maxDepth))
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizationService.localizedString("bottomTime", table: "feed"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(formatBottomTime(diveLog.bottomTime))
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
                
                if !diveLog.fishSpecies.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizationService.localizedString("fish", table: "feed"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("\(diveLog.fishSpecies.count)")
                            .font(.headline)
                            .foregroundStyle(.primary)
                    }
                }
            }
        }
        .padding()
        .background(Color(uiColor: .tertiarySystemGroupedBackground))
        .cornerRadius(8)
    }
    
    private func formatDepth(_ meters: Double) -> String {
        guard meters.isFinite, meters >= 0, meters <= 500 else {
            return "—"
        }
        if settingsService.measurementUnits.depth == .feet {
            let feet = Int(meters.metersToFeet())
            return "\(feet)ft"
        }
        return "\(Int(meters))m"
    }

    private func formatBottomTime(_ minutes: Int) -> String {
        guard minutes >= 0, minutes <= 24 * 60 else {
            return "—"
        }
        return "\(minutes) min"
    }
}

struct PostCommentsView: View {
    let postId: String
    @StateObject private var viewModel = FeedViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    @State private var commentText = ""
    
    var body: some View {
        NavigationStack {
            VStack {
                List {
                    ForEach(viewModel.comments) { comment in
                        CommentRow(comment: comment)
                    }
                }
                
                HStack {
                    TextField(localizationService.localizedString("addComment", table: "feed"), text: $commentText)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    Button(localizationService.localizedString("post", table: "feed")) {
                        Task {
                            await viewModel.addComment(postId: postId, content: commentText)
                            commentText = ""
                        }
                    }
                    .disabled(commentText.isEmpty)
                }
                .padding()
            }
            .navigationTitle(localizationService.localizedString("comments", table: "feed"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_feed_done".localized) {
                        dismiss()
                    }
                }
            }
            .task {
                await viewModel.loadComments(postId: postId)
            }
        }
    }
}

struct CommentRow: View {
    let comment: FeedComment
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let user = comment.user {
                if let avatarURL = user.avatarURL, let url = URL(string: avatarURL.hasPrefix("http") ? avatarURL : NetworkService.shared.baseURL + avatarURL) {
                    AsyncImage(url: url) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Image(systemName: "person.circle.fill")
                            .foregroundColor(.secondary)
                    }
                    .frame(width: 32, height: 32)
                    .clipShape(Circle())
                } else {
                    Image(systemName: "person.circle.fill")
                        .foregroundColor(.secondary)
                        .frame(width: 32, height: 32)
                }
            }
            
            VStack(alignment: .leading, spacing: 4) {
                if let user = comment.user {
                    Text(user.displayName)
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                Text(comment.content)
                    .font(.body)
                Text(comment.createdAt.formatted(date: .abbreviated, time: .shortened))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
    }
}

struct RecommendationsSection: View {
    let recommendations: [FeedRecommendation]
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("recommendations", table: "feed"))
                .font(.headline)
                .foregroundStyle(.primary)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(recommendations) { recommendation in
                        RecommendationCard(recommendation: recommendation)
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

struct RecommendationCard: View {
    let recommendation: FeedRecommendation
    
    var iconName: String {
        switch recommendation.type {
        case .location:
            return "location.fill"
        case .friend:
            return "person.2.fill"
        case .diveSite:
            return "divehub.logo"
        }
    }
    
    var body: some View {
        Button(action: recommendation.action) {
            VStack(alignment: .leading, spacing: 8) {
                DiveHubSystemIcon(name: iconName, color: .divePrimary, size: 26)
                
                Text(recommendation.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                
                Text(recommendation.description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(3)
                    .minimumScaleFactor(0.9)
            }
            .padding(12)
            .frame(width: 220, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(Color.primary.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
