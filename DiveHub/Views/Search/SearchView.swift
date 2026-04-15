//
//  SearchView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct SearchView: View {
    @State private var searchText = ""
    @State private var searchCategory: SearchCategory = .all
    @StateObject private var viewModel = SearchViewModel()
    @Environment(\.dismiss) var dismiss
    
    enum SearchCategory: String, CaseIterable {
        case all = "All"
        case sites = "Dive Sites"
        case centers = "Dive Centers"
        case instructors = "Instructors"
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Search Bar
                HStack {
                    TextField("ui_search_search_3".localized, text: $searchText)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .onSubmit {
                            viewModel.search(query: searchText, category: searchCategory)
                        }
                    
                    Button("ui_cancel".localized) {
                        dismiss()
                    }
                }
                .padding()
                
                // Category Picker
                Picker("ui_profile_category".localized, selection: $searchCategory) {
                    ForEach(SearchCategory.allCases, id: \.self) { category in
                        Text(category.rawValue).tag(category)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                
                // Results
                if viewModel.isLoading {
                    Spacer()
                    ProgressView()
                    Spacer()
                } else if !viewModel.hasResults && !searchText.isEmpty {
                    Spacer()
                    VStack(spacing: 16) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 50))
                            .foregroundColor(.secondary)
                        Text("ui_search_no_results_found".localized)
                            .font(.headline)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                } else {
                    List {
                        if !viewModel.diveSites.isEmpty {
                            Section("ui_dive_sites".localized) {
                                ForEach(viewModel.diveSites) { site in
                                    NavigationLink(destination: DiveSiteDetailView(site: site)) {
                                        SearchResultRow(title: site.displayName, subtitle: site.siteType.displayName, icon: "divehub.logo")
                                    }
                                }
                            }
                        }
                        
                        if !viewModel.diveCenters.isEmpty {
                            Section("ui_dive_centers".localized) {
                                ForEach(viewModel.diveCenters) { center in
                                    NavigationLink(destination: DiveCenterDetailView(center: center)) {
                                        SearchResultRow(title: center.name, subtitle: center.location.city, icon: "building.2")
                                    }
                                }
                            }
                        }
                        
                        if !viewModel.instructors.isEmpty {
                            Section("ui_profile_instructors".localized) {
                                ForEach(viewModel.instructors) { instructor in
                                    NavigationLink(destination: InstructorDetailView(instructor: instructor)) {
                                        SearchResultRow(title: instructor.name, subtitle: instructor.certifications.joined(separator: ", "), icon: "person.circle")
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("ui_search_search".localized)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct SearchResultRow: View {
    let title: String
    let subtitle: String
    let icon: String
    
    var body: some View {
        HStack {
            DiveHubSystemIcon(name: icon, color: .divePrimary, size: 22)
                .frame(width: 30)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

@MainActor
class SearchViewModel: ObservableObject {
    @Published var diveSites: [DiveSite] = []
    @Published var diveCenters: [DiveCenter] = []
    @Published var instructors: [Instructor] = []
    @Published var isLoading = false
    
    var hasResults: Bool {
        !diveSites.isEmpty || !diveCenters.isEmpty || !instructors.isEmpty
    }
    
    func search(query: String, category: SearchView.SearchCategory) {
        guard !query.isEmpty else { return }
        isLoading = true
        
        Task {
            // TODO: Implement actual search API
            // For now, filter mock data
            isLoading = false
        }
    }
}

#Preview {
    SearchView()
}
