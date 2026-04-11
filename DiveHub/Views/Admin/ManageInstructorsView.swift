//
//  ManageInstructorsView.swift
//  DiveHub
//
//  Created for managing dive center instructors
//

import SwiftUI

struct ManageInstructorsView: View {
    let center: DiveCenter
    @State private var instructors: [Instructor] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showAddInstructor = false
    @State private var searchText = ""
    @State private var searchResults: [User] = []
    @State private var isSearching = false
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        List {
            if isLoading {
                ProgressView()
            } else if let error = errorMessage {
                Text("\(localizationService.localizedString("error", table: "common")): \(error)")
                    .foregroundColor(.red)
            } else {
                Section {
                    Button(action: {
                        showAddInstructor = true
                    }) {
                        HStack {
                            Image(systemName: "person.badge.plus")
                            Text(localizationService.localizedString("addInstructor", table: "admin"))
                        }
                    }
                }
                
                Section(localizationService.localizedString("instructors", table: "admin")) {
                    if instructors.isEmpty {
                        Text(localizationService.localizedString("noInstructors", table: "admin"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(instructors) { instructor in
                            NavigationLink(destination: InstructorDetailView(instructor: instructor)) {
                                HStack {
                                    AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.photoURL ?? instructor.avatarURL) ?? "")) { image in
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
                                        Text(instructor.name)
                                            .font(.headline)
                                        if !instructor.trainingSystems.isEmpty {
                                            Text(instructor.trainingSystems.joined(separator: ", "))
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                    
                                    Spacer()
                                    
                                    Button(action: {
                                        removeInstructor(instructor)
                                    }) {
                                        Image(systemName: "trash")
                                            .foregroundColor(.red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("manageInstructors", table: "admin"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(localizationService.localizedString("done", table: "common")) {
                    dismiss()
                }
            }
        }
        .sheet(isPresented: $showAddInstructor) {
            AddInstructorView(
                centerId: center.id,
                existingInstructorUserIds: Set(instructors.map(\.userId)),
                onInstructorAdded: { instructor in
                    instructors.append(instructor)
                    showAddInstructor = false
                }
            )
        }
        .task {
            await loadInstructors()
        }
    }
    
    private func loadInstructors() async {
        isLoading = true
        errorMessage = nil
        
        
        do {
            instructors = try await NetworkService.shared.getDiveCenterInstructors(diveCenterId: center.id)
            
        } catch {
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
    
    private func removeInstructor(_ instructor: Instructor) {
        Task {
            do {
                try await NetworkService.shared.removeInstructorFromDiveCenter(instructorId: instructor.id, diveCenterId: center.id)
                await loadInstructors()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}

struct AddInstructorView: View {
    let centerId: String
    /// User IDs already linked as instructors at this center (admin API uses user id; public API uses `Instructor.userId`).
    let existingInstructorUserIds: Set<String>
    let onInstructorAdded: (Instructor) -> Void
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var searchText = ""
    @State private var searchResults: [User] = []
    @State private var isSearching = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationView {
            List {
                Section {
                    TextField(localizationService.localizedString("searchByName", table: "admin"), text: $searchText)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: searchText) { oldValue, newValue in
                            if newValue.count >= 2 {
                                searchUsers(query: newValue)
                            } else {
                                searchResults = []
                            }
                        }
                }
                
                if isSearching {
                    Section {
                        ProgressView()
                    }
                } else if !searchResults.isEmpty {
                    Section(localizationService.localizedString("searchResults", table: "admin")) {
                        ForEach(searchResults) { user in
                            HStack {
                                AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: user.avatarURL) ?? "")) { image in
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Image(systemName: "person.circle.fill")
                                        .foregroundColor(.secondary)
                                }
                                .frame(width: 40, height: 40)
                                .clipShape(Circle())
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(user.displayName)
                                        .font(.headline)
                                        .foregroundColor(.primary)
                                    Text(user.email)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                    if let certification = user.certificationLevel {
                                        Text(certification)
                                            .font(.caption2)
                                            .foregroundColor(.divePrimary)
                                    }
                                }
                                
                                Spacer()
                                
                                if existingInstructorUserIds.contains(user.id) {
                                    Text(localizationService.localizedString("alreadyAdded", table: "admin"))
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                } else {
                                    Button(action: {
                                        addInstructor(user: user)
                                    }) {
                                        Image(systemName: "plus.circle.fill")
                                            .foregroundColor(.divePrimary)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                } else if !searchText.isEmpty && searchText.count >= 2 {
                    Section {
                        Text(localizationService.localizedString("noResults", table: "common"))
                            .font(.caption)
                            .foregroundColor(.secondary)
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
            .navigationTitle(localizationService.localizedString("addInstructor", table: "admin"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func searchUsers(query: String) {
        guard !query.isEmpty else { return }
        
        isSearching = true
        errorMessage = nil
        
        Task {
            do {
                
                let results = try await NetworkService.shared.searchUsers(query: query)
                
                
                // Don't filter by role - allow adding any user as instructor
                searchResults = results
                
            } catch {
                errorMessage = error.localizedDescription
                searchResults = []
            }
            
            isSearching = false
        }
    }
    
    private func addInstructor(user: User) {
        Task {
            
            do {
                let instructor = try await NetworkService.shared.addInstructorToDiveCenter(userId: user.id, diveCenterId: centerId)
                
                
                onInstructorAdded(instructor)
                dismiss()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}

#Preview {
    NavigationView {
        ManageInstructorsView(center: DiveCenter(
            id: "1",
            name: "Test Center",
            description: "Test",
            location: DiveCenter.Location(
                latitude: 20.0,
                longitude: -80.0,
                address: "123 Test",
                city: "Test City",
                country: "Test Country"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+1234567890",
                email: "test@test.com",
                website: nil,
                socialMedia: nil
            ),
            photos: [],
            videos: [],
            averageRating: 4.5,
            reviewCount: 10,
            aiSummary: nil,
            instructors: [],
            affiliatedSites: [],
            services: [],
            operatingHours: DiveCenter.OperatingHours(),
            createdAt: Date(),
            updatedAt: Date()
        ))
    }
}
