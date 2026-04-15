//
//  StatisticsView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct StatisticsView: View {
    @StateObject private var viewModel = StatisticsViewModel()
    
    var body: some View {
        ScrollView {
                VStack(spacing: 24) {
                    // Summary Cards
                    HStack(spacing: 16) {
                        StatCard(title: "Total Dives", value: "\(viewModel.statistics.totalDives)", icon: "divehub.logo", color: .blue)
                        StatCard(title: "Bottom Time", value: "\(viewModel.statistics.totalBottomTime)h", icon: "clock", color: .green)
                    }
                    .padding(.horizontal)
                    
                    HStack(spacing: 16) {
                        StatCard(title: "Deepest", value: "\(Int(viewModel.statistics.deepestDive))m", icon: "arrow.down", color: .purple)
                        StatCard(title: "Longest", value: "\(viewModel.statistics.longestDive)min", icon: "timer", color: .orange)
                    }
                    .padding(.horizontal)
                    
                    // Charts
                    VStack(alignment: .leading, spacing: 16) {
                        Text("ui_statistics_dives_by_month".localized)
                            .font(.headline)
                            .padding(.horizontal)
                        
                        // Simple bar chart representation
                        VStack(spacing: 8) {
                            ForEach(Array(viewModel.statistics.diveByMonth.keys.sorted()), id: \.self) { month in
                                HStack {
                                    Text(month)
                                        .font(.caption)
                                        .frame(width: 80, alignment: .leading)
                                    
                                    GeometryReader { geometry in
                                        HStack(spacing: 0) {
                                            Rectangle()
                                                .fill(Color.divePrimary)
                                                .frame(width: geometry.size.width * CGFloat(viewModel.statistics.diveByMonth[month] ?? 0) / CGFloat(viewModel.statistics.diveByMonth.values.max() ?? 1))
                                        }
                                    }
                                    .frame(height: 20)
                                    
                                    Text("ui_statistics_value".localized)
                                        .font(.caption)
                                        .frame(width: 30, alignment: .trailing)
                                }
                            }
                        }
                        .padding()
                        .cardStyle()
                        .padding(.horizontal)
                    }
                    
                    // Milestones
                    if !viewModel.statistics.milestones.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("ui_statistics_recent_milestones".localized)
                                .font(.headline)
                                .padding(.horizontal)
                            
                            ForEach(viewModel.statistics.milestones.prefix(5)) { milestone in
                                MilestoneRow(milestone: milestone)
                            }
                            .padding(.horizontal)
                        }
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle("ui_statistics_statistics".localized)
            .diveHubNavigationChrome()
            .task {
                await viewModel.loadStatistics()
            }
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            DiveHubSystemIcon(name: icon, color: color, size: 24)
            
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .cardStyle()
    }
}

struct MilestoneRow: View {
    let milestone: DiveStatistics.Milestone
    
    var body: some View {
        HStack {
            Image(systemName: "trophy.fill")
                .foregroundColor(.diveAccent)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(milestone.title)
                    .font(.headline)
                Text(milestone.description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Text(milestone.achievedAt.formatted(date: .medium, time: .none))
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .cardStyle()
    }
}

@MainActor
class StatisticsViewModel: ObservableObject {
    @Published var statistics = DiveStatistics(
        totalDives: 0,
        totalBottomTime: 0,
        deepestDive: 0,
        longestDive: 0,
        averageDepth: 0,
        averageWaterTemperature: nil,
        averageVisibility: nil,
        uniqueDiveSitesCount: 0,
        uniqueDiveCentersCount: 0,
        favoriteSites: [],
        diveByMonth: [:],
        diveByType: [:],
        milestones: []
    )
    
    func loadStatistics() async {
        // TODO: Load from API or calculate from logs
    }
}

#Preview {
    NavigationStack {
        StatisticsView()
    }
}
