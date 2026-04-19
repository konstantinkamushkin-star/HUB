//
//  SocialTabView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct SocialTabView: View {
    /// One shared model for friends list, requests sheet, and tracking tab so accept/add updates all UIs.
    @StateObject private var socialViewModel = SocialViewModel()
    @State private var selectedTab = 0
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        NavigationStack {
            VStack {
                Picker("", selection: $selectedTab) {
                    Text(localizationService.localizedString("friends", table: "social")).tag(0)
                    Text(localizationService.localizedString("groupTrips", table: "social")).tag(1)
                    Text(localizationService.localizedString("tracking", table: "social")).tag(2)
                }
                .pickerStyle(.segmented)
                .padding()
                
                TabView(selection: $selectedTab) {
                    FriendsView()
                        .environmentObject(socialViewModel)
                        .tag(0)
                    
                    GroupTripsView()
                        .tag(1)
                    
                    FriendTrackingView()
                        .environmentObject(socialViewModel)
                        .tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .navigationTitle(localizationService.localizedString("social", table: "common"))
            .diveHubNavigationChrome()
        }
    }
}

struct FriendsView: View {
    @EnvironmentObject private var viewModel: SocialViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showAddFriend = false
    @State private var showFriendRequests = false
    @State private var searchText = ""
    
    var body: some View {
        List {
            Section {
                Button(action: { showAddFriend = true }) {
                    HStack {
                        Image(systemName: "person.badge.plus")
                        Text(localizationService.localizedString("addFriend", table: "social"))
                    }
                }
                Button(action: { showFriendRequests = true }) {
                    HStack {
                        Image(systemName: "person.2.fill")
                        Text(localizationService.localizedString("friendRequests", table: "social"))
                    }
                }
            }
            
            Section(localizationService.localizedString("friends", table: "social")) {
                if viewModel.isLoading {
                    ProgressView()
                } else if let error = viewModel.error {
                    Text("Error: \(error.localizedDescription)")
                        .foregroundColor(.red)
                } else if viewModel.friends.isEmpty {
                    Text(localizationService.localizedString("noFriends", table: "social"))
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(viewModel.friends) { friend in
                        NavigationLink(destination: UserProfileView(userId: friend.id)) {
                            FriendRow(friend: friend)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $searchText, prompt: localizationService.localizedString("search", table: "common"))
        .task {
            await viewModel.loadFriends()
        }
        .sheet(isPresented: $showAddFriend) {
            AddFriendView()
                .environmentObject(viewModel)
                .onDisappear {
                    Task {
                        await viewModel.loadFriends()
                    }
                }
        }
        .sheet(isPresented: $showFriendRequests) {
            FriendRequestsView()
                .environmentObject(viewModel)
                .onDisappear {
                    Task {
                        await viewModel.loadFriends()
                    }
                }
        }
        .refreshable {
            await viewModel.loadFriends()
        }
    }
}

struct FriendRow: View {
    let friend: User
    
    var body: some View {
        HStack {
            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: friend.avatarURL) ?? "")) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "person.circle.fill")
                    .foregroundColor(.secondary)
            }
            .frame(width: 50, height: 50)
            .clipShape(Circle())
            
            VStack(alignment: .leading) {
                Text(friend.displayName)
                    .font(.headline)
                Text(friend.role.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
    }
}

struct GroupTripsView: View {
    @StateObject private var viewModel = SocialViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showCreateTrip = false
    
    var body: some View {
        List {
            Section {
                Button(action: { showCreateTrip = true }) {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text(localizationService.localizedString("createTrip", table: "social"))
                    }
                }
            }
            
            Section(localizationService.localizedString("groupTrips", table: "social")) {
                if viewModel.groupTrips.isEmpty {
                    Text(localizationService.localizedString("noTrips", table: "social"))
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(viewModel.groupTrips) { trip in
                        GroupTripRow(trip: trip)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .task {
            await viewModel.loadGroupTrips()
        }
        .sheet(isPresented: $showCreateTrip) {
            CreateGroupTripView()
        }
    }
}

struct GroupTripRow: View {
    let trip: GroupTrip
    
    var body: some View {
        VStack(alignment: .leading) {
            Text(trip.name)
                .font(.headline)
            Text(trip.description ?? "")
                .font(.caption)
                .foregroundColor(.secondary)
            HStack {
                Label("\(trip.participants.count)", systemImage: "person.2")
                Spacer()
                Text(trip.startDate, style: .date)
            }
            .font(.caption)
            .foregroundColor(.secondary)
        }
    }
}

struct FriendTrackingView: View {
    @EnvironmentObject private var viewModel: SocialViewModel
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        List {
            Section(localizationService.localizedString("friendTracking", table: "social")) {
                ForEach(viewModel.friends) { friend in
                    HStack {
                        AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: friend.avatarURL) ?? "")) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Image(systemName: "person.circle.fill")
                        }
                        .frame(width: 40, height: 40)
                        .clipShape(Circle())
                        
                        VStack(alignment: .leading) {
                            Text(friend.displayName)
                                .font(.headline)
                            Text(localizationService.localizedString("lastSeen", table: "social"))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        Toggle("", isOn: .constant(false))
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .task {
            await viewModel.loadFriends()
        }
    }
}

struct AddFriendView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @EnvironmentObject private var viewModel: SocialViewModel
    @State private var searchText = ""
    @State private var searchResults: [User] = []
    @State private var isSearching = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField(localizationService.localizedString("searchByNameOrEmail", table: "social"), text: $searchText)
                            .onSubmit {
                                Task {
                                    await searchUsers()
                                }
                            }
                            .onChange(of: searchText) { oldValue, newValue in
                                // Auto-search when user types (debounced)
                                if !newValue.isEmpty && newValue.count >= 2 {
                                    Task {
                                        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 second delay
                                        if searchText == newValue { // Only search if text hasn't changed
                                            await searchUsers()
                                        }
                                    }
                                } else if newValue.isEmpty {
                                    searchResults = []
                                }
                            }
                        if isSearching {
                            ProgressView()
                                .padding(.leading, 8)
                        }
                        if !searchText.isEmpty && !isSearching {
                            Button(action: {
                                Task {
                                    await searchUsers()
                                }
                            }) {
                                Image(systemName: "magnifyingglass")
                            }
                        }
                    }
                }
                
                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }
                
                Section(localizationService.localizedString("results", table: "social")) {
                    if searchResults.isEmpty && !searchText.isEmpty && !isSearching {
                        Text(localizationService.localizedString("noResults", table: "common"))
                            .foregroundColor(.secondary)
                            .font(.caption)
                    } else {
                        ForEach(searchResults, id: \.id) { user in
                            HStack {
                                AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: user.avatarURL) ?? "")) { image in
                                    image.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Image(systemName: "person.circle.fill")
                                }
                                .frame(width: 40, height: 40)
                                .clipShape(Circle())
                                
                                VStack(alignment: .leading) {
                                    Text(user.displayName)
                                        .font(.headline)
                                    Text(user.email)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                                
                                Button(localizationService.localizedString("add")) {
                                    Task {
                                        await sendFriendRequest(userId: user.id)
                                    }
                                }
                                .buttonStyle(.bordered)
                            }
                            .id(user.id) // Explicit ID for SwiftUI updates
                        }
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("addFriend", table: "social"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("done", table: "common")) {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func searchUsers() async {
        
        guard !searchText.isEmpty else {
            searchResults = []
            return
        }
        
        isSearching = true
        errorMessage = nil
        
        do {
            let results = try await NetworkService.shared.searchUsers(query: searchText)
            // Filter out current user on client side as well (double check)
            if let currentUserId = AuthenticationService.shared.currentUser?.id {
                searchResults = results.filter { $0.id != currentUserId }
            } else {
                searchResults = results
            }
        } catch let error as NetworkError {
            
            // If endpoint doesn't exist (404), show user-friendly message
            if case .serverError(404) = error {
                errorMessage = "User search is not available yet. This feature will be available soon."
            } else {
                errorMessage = "Failed to search users: \(error.localizedDescription)"
            }
            searchResults = []
        } catch {
            errorMessage = "Failed to search users: \(error.localizedDescription)"
            searchResults = []
        }
        
        isSearching = false
    }
    
    private func sendFriendRequest(userId: String) async {
        do {
            try await viewModel.sendFriendRequest(userId: userId)
            // Remove from search results after sending request
            searchResults = searchResults.filter { $0.id != userId }
            errorMessage = nil
        } catch let error as FriendRequestError {
            // Even if request already exists, remove from search results
            searchResults = searchResults.filter { $0.id != userId }
            errorMessage = error.localizedDescription
        } catch let error as NetworkError {
            // If it's a 400 error (already exists), remove from results
            if case .serverError(400) = error {
                searchResults = searchResults.filter { $0.id != userId }
            }
            errorMessage = "Failed to send friend request: \(error.localizedDescription)"
        } catch {
            errorMessage = "Failed to send friend request: \(error.localizedDescription)"
        }
    }
}

struct CreateGroupTripView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var tripName = ""
    @State private var tripDescription = ""
    @State private var startDate = Date()
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizationService.localizedString("tripName", table: "social"), text: $tripName)
                    TextField(localizationService.localizedString("description", table: "settings"), text: $tripDescription, axis: .vertical)
                        .lineLimit(3...6)
                    DatePicker(localizationService.localizedString("startDate", table: "settings"), selection: $startDate, displayedComponents: .date)
                }
            }
            .navigationTitle(localizationService.localizedString("createTrip", table: "social"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {
                        // TODO: Create trip
                        dismiss()
                    }
                    .disabled(tripName.isEmpty)
                }
            }
        }
    }
}

struct FriendRequestsView: View {
    @EnvironmentObject private var viewModel: SocialViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    @State private var selectedTab = 0
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("", selection: $selectedTab) {
                    Text(localizationService.localizedString("received", table: "social")).tag(0)
                    Text(localizationService.localizedString("sent", table: "social")).tag(1)
                }
                .pickerStyle(.segmented)
                .padding()
                
                if selectedTab == 0 {
                    receivedRequestsView
                } else {
                    sentRequestsView
                }
            }
            .navigationTitle(localizationService.localizedString("friendRequests", table: "social"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("done", table: "common")) {
                        dismiss()
                    }
                }
            }
            .task {
                await viewModel.loadReceivedFriendRequests()
                await viewModel.loadSentFriendRequests()
            }
        }
    }
    
    private var receivedRequestsView: some View {
        List {
            if viewModel.isLoading {
                ProgressView()
            } else if let error = viewModel.error {
                Text("Error: \(error.localizedDescription)")
                    .foregroundColor(.red)
            } else if viewModel.receivedFriendRequests.isEmpty {
                Text(localizationService.localizedString("noPendingRequests", table: "social"))
                    .foregroundColor(.secondary)
                    .font(.caption)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(viewModel.receivedFriendRequests) { request in
                    FriendRequestRow(
                        user: request.user,
                        createdAt: request.createdAt,
                        onAccept: {
                            Task {
                                do {
                                    try await viewModel.acceptFriendRequest(userId: request.user.id)
                                    // Reload both requests and friends list
                                    await viewModel.loadReceivedFriendRequests()
                                    await viewModel.loadFriends()
                                } catch {
                                    // Error handling - show alert if needed
                                    print("Error accepting friend request: \(error)")
                                }
                            }
                        },
                        onDecline: {
                            Task {
                                do {
                                    try await viewModel.declineFriendRequest(friendshipId: request.id)
                                } catch {
                                    // Error handling
                                }
                            }
                        },
                        showActions: true
                    )
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable {
            await viewModel.loadReceivedFriendRequests()
        }
    }
    
    private var sentRequestsView: some View {
        List {
            if viewModel.isLoading {
                ProgressView()
            } else if let error = viewModel.error {
                Text("Error: \(error.localizedDescription)")
                    .foregroundColor(.red)
            } else if viewModel.sentFriendRequests.isEmpty {
                Text(localizationService.localizedString("noSentRequests", table: "social"))
                    .foregroundColor(.secondary)
                    .font(.caption)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(viewModel.sentFriendRequests) { request in
                    FriendRequestRow(
                        user: request.user,
                        createdAt: request.createdAt,
                        onAccept: nil,
                        onDecline: nil,
                        showActions: false
                    )
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable {
            await viewModel.loadSentFriendRequests()
        }
    }
}

struct FriendRequestRow: View {
    let user: User
    let createdAt: Date
    let onAccept: (() -> Void)?
    let onDecline: (() -> Void)?
    let showActions: Bool
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        HStack {
            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: user.avatarURL) ?? "")) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "person.circle.fill")
                    .foregroundColor(.secondary)
            }
            .frame(width: 50, height: 50)
            .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(user.displayName)
                    .font(.headline)
                Text(user.email)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(createdAt.formatted(date: .abbreviated, time: .omitted))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            if showActions, let onAccept = onAccept, let onDecline = onDecline {
                HStack(spacing: 8) {
                    Button(action: onAccept) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.title2)
                    }
                    Button(action: onDecline) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.red)
                            .font(.title2)
                    }
                }
            } else {
                Text(localizationService.localizedString("pending", table: "social"))
                    .font(.caption)
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.orange.opacity(0.1))
                    .cornerRadius(8)
            }
        }
    }
}

#Preview {
    SocialTabView()
}
