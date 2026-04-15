//
//  AchievementsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct AchievementsView: View {
    @StateObject private var viewModel = AchievementsViewModel()
    
    var body: some View {
        ScrollView {
                VStack(spacing: 24) {
                    // Progress Overview
                    VStack(spacing: 16) {
                        Text("ui_achievements_value_value_achievements".localized)
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        ProgressView(value: Double(viewModel.unlockedCount), total: Double(viewModel.totalCount))
                            .progressViewStyle(LinearProgressViewStyle(tint: .divePrimary))
                    }
                    .padding()
                    .cardStyle()
                    .padding(.horizontal)
                    
                    // Achievements Grid
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                        ForEach(viewModel.achievements) { achievement in
                            AchievementCard(achievement: achievement)
                        }
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .navigationTitle("ui_achievements_achievements".localized)
            .diveHubNavigationChrome()
            .task {
                await viewModel.loadAchievements()
            }
    }
}

struct AchievementCard: View {
    let achievement: Achievement
    
    var body: some View {
        VStack(spacing: 8) {
            DiveHubSystemIcon(
                name: achievement.iconName,
                color: achievement.unlockedAt != nil ? .diveAccent : .gray.opacity(0.3),
                size: 40
            )
            .frame(width: 60, height: 60)
            
            Text(achievement.title)
                .font(.caption)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(achievement.unlockedAt != nil ? Color.diveAccent.opacity(0.1) : Color(.systemGray6))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(achievement.unlockedAt != nil ? Color.diveAccent : Color.clear, lineWidth: 2)
        )
    }
}

@MainActor
class AchievementsViewModel: ObservableObject {
    @Published var achievements: [Achievement] = []
    
    var unlockedCount: Int {
        achievements.filter { $0.unlockedAt != nil }.count
    }
    
    var totalCount: Int {
        achievements.count
    }
    
    func loadAchievements() async {
        // TODO: Load from API
        // Mock data for now
        achievements = [
            Achievement(id: "1", title: "First Dive", description: "Complete your first dive", iconName: "divehub.logo", unlockedAt: Date()),
            Achievement(id: "2", title: "Deep Diver", description: "Dive deeper than 30m", iconName: "arrow.down", unlockedAt: nil),
            Achievement(id: "3", title: "Night Owl", description: "Complete 5 night dives", iconName: "moon.fill", unlockedAt: nil)
        ]
    }
}

#Preview {
    NavigationStack {
        AchievementsView()
    }
}
