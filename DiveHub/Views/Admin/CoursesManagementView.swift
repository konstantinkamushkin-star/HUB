//
//  CoursesManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct CoursesManagementView: View {
    @StateObject private var viewModel = CourseViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @EnvironmentObject var authService: AuthenticationService
    @State private var showCreateCourse = false
    @State private var selectedCourse: Course?
    
    var body: some View {
        NavigationView {
            contentView
                .navigationTitle(localizationService.localizedString("courses", table: "courses"))
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: {
                            showCreateCourse = true
                        }) {
                            Image(systemName: "plus")
                        }
                    }
                }
                .sheet(isPresented: $showCreateCourse) {
                    CreateCourseView(viewModel: viewModel)
                }
                .sheet(item: $selectedCourse) { course in
                    CourseDetailView(course: course, viewModel: viewModel)
                        .onDisappear {
                            Task {
                                await loadCoursesIfNeeded()
                            }
                        }
                }
                .task {
                    await loadCoursesIfNeeded()
                }
        }
        .onChange(of: showCreateCourse) { oldValue, newValue in
            if oldValue == true && newValue == false {
                Task {
                    await loadCoursesIfNeeded()
                }
            }
        }
        .onChange(of: authService.currentUser?.diveCenterId) { _, _ in
            Task {
                await loadCoursesIfNeeded()
            }
        }
    }
    
    @ViewBuilder
    private var contentView: some View {
        VStack {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.courses.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "book.closed")
                        .font(.system(size: 60))
                        .foregroundColor(.gray)
                    Text(localizationService.localizedString("noCoursesAvailable", table: "courses"))
                        .font(.headline)
                        .foregroundColor(.gray)
                    Button(localizationService.localizedString("createFirstCourse", table: "courses")) {
                        showCreateCourse = true
                    }
                    .buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.courses) { course in
                        CourseRowView(course: course)
                            .onTapGesture {
                                selectedCourse = course
                            }
                    }
                    .onDelete(perform: deleteCourses)
                }
            }
        }
    }
    
    private func loadCoursesIfNeeded() async {
        // Try to get diveCenterId - if it's nil, try to find dive center by email
        var diveCenterId = authService.currentUser?.diveCenterId
        if diveCenterId == nil && authService.currentUser?.role == .diveCenterAdmin {
            do {
                let diveCenters = try await NetworkService.shared.getDiveCenters()
                if let matchingCenter = diveCenters.first(where: { $0.contactInfo.email.lowercased() == authService.currentUser?.email.lowercased() }) {
                    diveCenterId = matchingCenter.id
                    // Update user with diveCenterId
                    if var user = authService.currentUser {
                        user.diveCenterId = matchingCenter.id
                        authService.updateUser(user)
                    }
                }
            } catch {
                print("Error fetching dive centers: \(error)")
            }
        }
        
        if let diveCenterId = diveCenterId {
            await viewModel.loadCourses(diveCenterId: diveCenterId)
        }
    }
    
    private func deleteCourses(at offsets: IndexSet) {
        for index in offsets {
            let course = viewModel.courses[index]
            Task {
                do {
                    try await viewModel.deleteCourse(courseId: course.id)
                    // Reload courses after deletion
                    await loadCoursesIfNeeded()
                } catch {
                    print("Error deleting course: \(error)")
                }
            }
        }
    }
}

struct CourseRowView: View {
    let course: Course
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(course.name)
                    .font(.headline)
                Spacer()
                Text(course.level.displayName)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(levelColor(for: course.level).opacity(0.2))
                    .foregroundColor(levelColor(for: course.level))
                    .cornerRadius(8)
            }
            
            Text(course.description)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            
            HStack {
                    Label("\(course.duration) \(localizationService.localizedString("days", table: "courses"))", systemImage: "calendar")
                    .font(.caption)
                Spacer()
                if !course.trainingSystems.isEmpty {
                    Label(course.trainingSystems.joined(separator: ", "), systemImage: "certificate")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }
    
    private func levelColor(for level: Course.CourseLevel) -> Color {
        switch level {
        case .basic: return .blue
        case .advanced: return .green
        case .professional: return .orange
        case .technical: return .red
        case .specialization: return .purple
        }
    }
}

struct CourseDetailView: View {
    let initialCourse: Course
    @ObservedObject var viewModel: CourseViewModel
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var authService: AuthenticationService
    @State private var showEdit = false
    @State private var showBooking = false
    @StateObject private var localizationService = LocalizationService.shared
    @State private var currentCourse: Course
    @State private var refreshId = UUID()
    
    init(course: Course, viewModel: CourseViewModel) {
        self.initialCourse = course
        self.viewModel = viewModel
        _currentCourse = State(initialValue: course)
    }
    
    // Update currentCourse when viewModel.courses changes
    private func updateCurrentCourse() {
        guard let updatedCourse = viewModel.courses.first(where: { $0.id == initialCourse.id }) else {
            return
        }
        
        // Only update if course data actually changed to avoid infinite loops
        if updatedCourse.id == currentCourse.id &&
           updatedCourse.name == currentCourse.name &&
           updatedCourse.description == currentCourse.description {
            return
        }
        
        currentCourse = updatedCourse
        refreshId = UUID() // Force view refresh
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(currentCourse.name)
                            .font(.title)
                            .bold()
                        
                        Text(currentCourse.level.displayName)
                            .font(.subheadline)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.blue.opacity(0.2))
                            .cornerRadius(8)
                        
                        Text(currentCourse.description)
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    
                    Divider()
                    
                    if !currentCourse.trainingSystems.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(localizationService.localizedString("trainingSystems", table: "courses"))
                                .font(.headline)
                            ForEach(currentCourse.trainingSystems, id: \.self) { system in
                                HStack {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.green)
                                    Text(system)
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizationService.localizedString("program", table: "courses"))
                            .font(.headline)
                        
                        ForEach(currentCourse.program.sorted(by: { $0.order < $1.order })) { module in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(module.title)
                                        .font(.subheadline)
                                        .bold()
                                    Spacer()
                                    Text("\(module.duration)\(localizationService.localizedString("hours", table: "courses"))")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Text(module.description)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    
                    if let prerequisites = currentCourse.prerequisites, !prerequisites.isEmpty {
                        Divider()
                        VStack(alignment: .leading, spacing: 8) {
                            Text(localizationService.localizedString("prerequisites", table: "courses"))
                                .font(.headline)
                            ForEach(prerequisites, id: \.self) { prereq in
                                Text("• \(prereq)")
                                    .font(.subheadline)
                            }
                        }
                    }
                }
                .padding()
            }
            .id(refreshId) // Force view refresh when refreshId changes
            .navigationTitle(localizationService.localizedString("courseDetails", table: "courses"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if canBookCourse {
                        Button("Book") {
                            showBooking = true
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("edit", table: "common")) {
                        showEdit = true
                    }
                }
            }
            .sheet(isPresented: $showEdit) {
                CreateCourseView(course: currentCourse, viewModel: viewModel)
            }
            .sheet(isPresented: $showBooking) {
                CourseBookingView(course: currentCourse, courseViewModel: viewModel)
            }
            .onChange(of: showEdit) { oldValue, newValue in
                // Reload course data when edit sheet is dismissed
                if oldValue == true && newValue == false {
                    updateCurrentCourse()
                }
            }
            .onChange(of: viewModel.courses) { oldCourses, newCourses in
                // Update currentCourse when viewModel.courses changes
                updateCurrentCourse()
            }
            .onAppear {
                updateCurrentCourse()
            }
        }
    }
    
    private var canBookCourse: Bool {
        guard let user = authService.currentUser else { return false }
        return user.role == .diverBasic || user.role == .diverPro
    }
}

struct CreateCourseView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var viewModel: CourseViewModel
    @EnvironmentObject var authService: AuthenticationService
    @StateObject private var localizationService = LocalizationService.shared
    
    var course: Course? // For editing
    
    @State private var name: String = ""
    @State private var level: Course.CourseLevel = .basic
    @State private var description: String = ""
    @State private var trainingSystems: [String] = []
    @State private var duration: Int = 1
    @State private var prerequisites: [String] = []
    @State private var modules: [Course.CourseModule] = []
    @State private var selectedTrainingSystem: String = ""
    
    let availableTrainingSystems = ["PADI", "SSI", "NAUI", "CMAS", "BSAC", "SDI", "TDI"]
    
    init(course: Course? = nil, viewModel: CourseViewModel) {
        self.course = course
        self.viewModel = viewModel
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section("Basic Information") {
                    TextField(localizationService.localizedString("courseName", table: "courses"), text: $name)
                    Picker(localizationService.localizedString("level", table: "courses"), selection: $level) {
                        ForEach([Course.CourseLevel.basic, .advanced, .professional, .technical, .specialization], id: \.self) { level in
                            Text(level.displayName).tag(level)
                        }
                    }
                    TextEditor(text: $description)
                        .frame(height: 100)
                    Stepper("\(localizationService.localizedString("duration", table: "courses")): \(duration) \(localizationService.localizedString("days", table: "courses"))", value: $duration, in: 1...30)
                }
                
                Section(localizationService.localizedString("trainingSystems", table: "courses")) {
                    ForEach(trainingSystems, id: \.self) { system in
                        HStack {
                            Text(system)
                            Spacer()
                            Button(action: {
                                trainingSystems.removeAll { $0 == system }
                            }) {
                                Image(systemName: "minus.circle.fill")
                                    .foregroundColor(.red)
                            }
                        }
                    }
                    
                    Picker(localizationService.localizedString("addTrainingSystem", table: "courses"), selection: $selectedTrainingSystem) {
                        Text(localizationService.localizedString("select", table: "courses")).tag("")
                        ForEach(availableTrainingSystems.filter { !trainingSystems.contains($0) }, id: \.self) { system in
                            Text(system).tag(system)
                        }
                    }
                    .onChange(of: selectedTrainingSystem) { oldValue, newValue in
                        if !newValue.isEmpty && !trainingSystems.contains(newValue) {
                            trainingSystems.append(newValue)
                            selectedTrainingSystem = ""
                        }
                    }
                }
                
                Section(localizationService.localizedString("prerequisites", table: "courses")) {
                    ForEach(prerequisites, id: \.self) { prereq in
                        HStack {
                            Text(prereq)
                            Spacer()
                            Button(action: {
                                prerequisites.removeAll { $0 == prereq }
                            }) {
                                Image(systemName: "minus.circle.fill")
                                    .foregroundColor(.red)
                            }
                        }
                    }
                    
                    Button(localizationService.localizedString("addPrerequisite", table: "courses")) {
                        // TODO: Show text field to add prerequisite
                    }
                }
                
                Section(localizationService.localizedString("courseModules", table: "courses")) {
                    ForEach(modules.sorted(by: { $0.order < $1.order })) { module in
                        NavigationLink(destination: CourseModuleEditView(module: Binding(
                            get: { module },
                            set: { newModule in
                                if let index = modules.firstIndex(where: { $0.id == module.id }) {
                                    modules[index] = newModule
                                }
                            }
                        ))) {
                            VStack(alignment: .leading) {
                                Text(module.title)
                                    .font(.headline)
                                Text(module.moduleType.rawValue.capitalized)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    
                    Button(localizationService.localizedString("addModule", table: "courses")) {
                        let newModule = Course.CourseModule(
                            id: UUID().uuidString,
                            title: localizationService.localizedString("newModule", table: "courses"),
                            description: "",
                            duration: 1,
                            moduleType: .theory,
                            order: modules.count
                        )
                        modules.append(newModule)
                    }
                }
            }
            .navigationTitle(course == nil ? localizationService.localizedString("createCourse", table: "courses") : localizationService.localizedString("editCourse", table: "courses"))
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save", table: "common")) {
                        Task {
                            await saveCourse()
                        }
                    }
                    .disabled(!isFormValid)
                }
            }
            .onAppear {
                if let course = course {
                    loadCourseData(course)
                }
            }
        }
    }
    
    private var isFormValid: Bool {
        !name.isEmpty && !description.isEmpty && duration > 0
    }
    
    private func loadCourseData(_ course: Course) {
        name = course.name
        level = course.level
        description = course.description
        trainingSystems = course.trainingSystems
        duration = course.duration
        prerequisites = course.prerequisites ?? []
        modules = course.program
    }
    
    private func saveCourse() async {
        guard let user = authService.currentUser else {
            return
        }
        
        // Try to get diveCenterId - if it's nil, try to fetch updated user info or find dive center by email
        var diveCenterId = user.diveCenterId
        if diveCenterId == nil {
            do {
                let updatedUser = try await NetworkService.shared.getUser(userId: user.id)
                diveCenterId = updatedUser.diveCenterId
                // Update current user in auth service
                authService.updateUser(updatedUser)
            } catch {
                print("Error fetching user: \(error)")
            }
        }
        
        // If still no diveCenterId, try to find dive center by user email (for DIVE_CENTER_ADMIN)
        if diveCenterId == nil && user.role == .diveCenterAdmin {
            do {
                let diveCenters = try await NetworkService.shared.getDiveCenters()
                // Find dive center where contact email matches user email
                if let matchingCenter = diveCenters.first(where: { $0.contactInfo.email.lowercased() == user.email.lowercased() }) {
                    diveCenterId = matchingCenter.id
                    // Update user with diveCenterId
                    var updatedUser = user
                    updatedUser.diveCenterId = matchingCenter.id
                    authService.updateUser(updatedUser)
                }
            } catch {
                print("Error fetching dive centers: \(error)")
            }
        }
        
        // If still no diveCenterId, we can't create a course
        guard let finalDiveCenterId = diveCenterId else {
            print("Error: Cannot create course without diveCenterId. User role: \(user.role.rawValue), email: \(user.email)")
            // TODO: Show error alert to user
            return
        }
        
        // Create Course object for saving
        // NetworkService will handle DTO conversion for new courses (removing id, createdAt, updatedAt, module ids)
        let cleanedModules = modules.filter { !$0.description.isEmpty }
        
        let courseToSave = Course(
            id: course?.id ?? UUID().uuidString,
            name: name,
            level: level,
            description: description,
            trainingSystems: trainingSystems,
            program: cleanedModules,
            duration: duration,
            prerequisites: prerequisites.isEmpty ? nil : prerequisites,
            diveCenterId: finalDiveCenterId,
            instructorId: course?.instructorId,
            photos: course?.photos ?? [],
            createdAt: course?.createdAt ?? Date(),
            updatedAt: Date()
        )
        
        do {
            if course == nil {
                let _ = try await viewModel.createCourse(courseToSave)
                // Reload courses list after creation
                if let diveCenterId = authService.currentUser?.diveCenterId {
                    await viewModel.loadCourses(diveCenterId: diveCenterId)
                }
            } else {
                let _ = try await viewModel.updateCourse(courseToSave)
                // Reload courses list after update
                if let diveCenterId = authService.currentUser?.diveCenterId {
                    await viewModel.loadCourses(diveCenterId: diveCenterId)
                }
            }
            dismiss()
        } catch {
            print("Error saving course: \(error)")
        }
    }
}

struct CourseModuleEditView: View {
    @Binding var module: Course.CourseModule
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section("Module Information") {
                TextField(localizationService.localizedString("moduleTitle", table: "courses"), text: $module.title)
                TextEditor(text: $module.description)
                    .frame(height: 100)
                Stepper("\(localizationService.localizedString("moduleDuration", table: "courses")): \(module.duration) \(localizationService.localizedString("hours", table: "courses"))", value: $module.duration, in: 1...24)
                Picker(localizationService.localizedString("moduleType", table: "courses"), selection: $module.moduleType) {
                    ForEach([Course.CourseModule.ModuleType.theory, .confinedWater, .openWater, .exam], id: \.self) { type in
                        Text(type.rawValue.capitalized).tag(type)
                    }
                }
                Stepper("\(localizationService.localizedString("order", table: "courses")): \(module.order)", value: $module.order, in: 0...100)
            }
        }
        .navigationTitle(localizationService.localizedString("editModule", table: "courses"))
    }
}
