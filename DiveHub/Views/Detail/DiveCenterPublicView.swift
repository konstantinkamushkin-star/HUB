//
//  DiveCenterPublicView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct DiveCenterPublicView: View {
    let center: DiveCenter
    var onShowOnMap: (() -> Void)? = nil
    
    @StateObject private var viewModel: DiveCenterPublicViewModel
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedCourse: Course?
    @State private var selectedInstructor: Instructor?
    @State private var selectedTrip: Trip?
    @State private var showInstructorCourses = false
    @State private var instructorCourses: [Course] = []
    @State private var showBooking = false
    
    init(center: DiveCenter, onShowOnMap: (() -> Void)? = nil) {
        self.center = center
        self.onShowOnMap = onShowOnMap
        _viewModel = StateObject(wrappedValue: DiveCenterPublicViewModel(centerId: center.id))
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Header with Photo Gallery
                if !center.photos.isEmpty {
                    TabView {
                        ForEach(center.photos, id: \.self) { photoURL in
                            PhotoView(photoURL: photoURL)
                        }
                    }
                    .frame(height: 250)
                    .tabViewStyle(.page)
                }
                
                VStack(alignment: .leading, spacing: 20) {
                    // Title and Rating
                    headerSection
                    
                    Divider()
                    
                    // Location Section
                    locationSection
                    
                    Divider()
                    
                    // Courses Section
                    coursesSection
                    
                    Divider()
                    
                    // Instructors Section
                    instructorsSection
                    
                    Divider()
                    
                    // Upcoming Trips Section
                    upcomingTripsSection
                    
                    Divider()
                    
                    // Reviews Section
                    reviewsSection
                    
                    Divider()
                    
                    // Archived Trips Section
                    archivedTripsSection
                }
                .padding()
            }
        }
        .navigationTitle(center.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                if let onShowOnMap = onShowOnMap {
                    Button(action: onShowOnMap) {
                        Image(systemName: "map")
                    }
                }
                Button(action: { showBooking = true }) {
                    Text(localizationService.localizedString("book"))
                        .fontWeight(.semibold)
                }
            }
        }
        .sheet(isPresented: $showBooking) {
            BookingWizardView(diveCenterId: center.id)
        }
        .sheet(item: $selectedCourse) { course in
            CourseDetailPublicView(course: course)
        }
        .sheet(item: $selectedInstructor) { instructor in
            InstructorDetailView(instructor: instructor)
        }
        .sheet(item: $selectedTrip) { trip in
            TripDetailView(trip: trip, showBooking: .constant(false))
        }
        .sheet(isPresented: $showInstructorCourses) {
            NavigationStack {
                InstructorCoursesView(courses: instructorCourses, instructor: selectedInstructor)
            }
        }
        .task {
            await viewModel.loadData()
        }
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(center.name)
                .font(.title)
                .fontWeight(.bold)
            
            HStack {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                Text(String(format: "%.1f", center.averageRating))
                    .fontWeight(.semibold)
                Text("(\(center.reviewCount) \(localizationService.localizedString("reviews", table: "common")))")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
    
    // MARK: - Location Section
    private var locationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("location", table: "common"))
                .font(.headline)
            
            // Address
            if !center.location.address.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(.divePrimary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizationService.localizedString("address", table: "common"))
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text(center.location.address)
                            .font(.body)
                    }
                }
            }
            
            // Route Description
            if !center.displayDescription.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "map.fill")
                        .foregroundColor(.divePrimary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizationService.localizedString("routeDescription", table: "common"))
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text(center.displayDescription)
                            .font(.body)
                    }
                }
            }
            
            // Contacts
            VStack(alignment: .leading, spacing: 8) {
                Text(localizationService.localizedString("contacts", table: "common"))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                HStack(spacing: 16) {
                    if !center.contactInfo.phone.isEmpty {
                        Link(destination: URL(string: "tel:\(center.contactInfo.phone)")!) {
                            HStack(spacing: 4) {
                                Image(systemName: "phone.fill")
                                Text(center.contactInfo.phone)
                            }
                            .foregroundColor(.divePrimary)
                        }
                    }
                    
                    if !center.contactInfo.email.isEmpty {
                        Link(destination: URL(string: "mailto:\(center.contactInfo.email)")!) {
                            HStack(spacing: 4) {
                                Image(systemName: "envelope.fill")
                                Text(center.contactInfo.email)
                            }
                            .foregroundColor(.divePrimary)
                        }
                    }
                }
                
                if let website = center.contactInfo.website, !website.isEmpty {
                    Link(destination: URL(string: website)!) {
                        HStack(spacing: 4) {
                            Image(systemName: "globe")
                            Text(website)
                        }
                        .foregroundColor(.divePrimary)
                    }
                }
            }
        }
    }
    
    // MARK: - Courses Section
    private var coursesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("courses", table: "courses"))
                .font(.headline)
            
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if viewModel.courses.isEmpty {
                Text(localizationService.localizedString("noCoursesAvailable", table: "courses"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(viewModel.courses) { course in
                            CourseCard(course: course) {
                                selectedCourse = course
                            }
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
    }
    
    // MARK: - Instructors Section
    private var instructorsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("instructors", table: "admin"))
                .font(.headline)
            
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if viewModel.instructors.isEmpty {
                Text(localizationService.localizedString("noInstructorsAvailable", table: "trips"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(viewModel.instructors) { instructor in
                            InstructorCard(
                                instructor: instructor,
                                onTap: {
                                    selectedInstructor = instructor
                                },
                                onBook: {
                                    let courses = viewModel.getCoursesForInstructor(instructorId: instructor.id)
                                    if courses.isEmpty {
                                        // If no courses, open booking wizard
                                        showBooking = true
                                    } else {
                                        instructorCourses = courses
                                        selectedInstructor = instructor
                                        showInstructorCourses = true
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
    }
    
    // MARK: - Upcoming Trips Section
    private var upcomingTripsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("upcomingTrips", table: "trips"))
                .font(.headline)
            
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if viewModel.upcomingTrips.isEmpty {
                Text(localizationService.localizedString("noTrips", table: "social"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding()
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(viewModel.upcomingTrips) { trip in
                            TripCard(trip: trip) {
                                selectedTrip = trip
                            }
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
    }
    
    // MARK: - Reviews Section
    private var reviewsSection: some View {
        ReviewsSection(reviewableType: .diveCenter, reviewableId: center.id)
    }
    
    // MARK: - Archived Trips Section
    private var archivedTripsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizationService.localizedString("archivedTrips", table: "trips"))
                .font(.headline)
            
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if viewModel.archivedTrips.isEmpty {
                Text(localizationService.localizedString("noArchivedTrips", table: "trips"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding()
            } else {ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(viewModel.archivedTrips) { trip in
                            TripCard(trip: trip) {
                                selectedTrip = trip
                            }
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
    }
}

// MARK: - Course Card
struct CourseCard: View {
    let course: Course
    let onTap: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                // Course Photo Placeholder
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 280, height: 160)
                    .cornerRadius(12)
                    .overlay(
                        Image(systemName: "book.closed.fill")
                            .font(.system(size: 40))
                            .foregroundColor(.gray)
                    )
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(course.name)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(2)
                    
                    Text(course.displayDescription)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                    
                    HStack {
                        Text("\(course.duration) \(localizationService.localizedString("days", table: "common"))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Spacer()
                        
                        // Duration is shown above
                    }
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            }
            .frame(width: 280)
            .background(Color.diveCard)
            .cornerRadius(12)
            .shadow(radius: 2)
        }
        .buttonStyle(.plain)
    }
    
}

// MARK: - Instructor Card
struct InstructorCard: View {
    let instructor: Instructor
    let onTap: () -> Void
    let onBook: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Instructor Photo
            AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.photoURL ?? instructor.avatarURL) ?? "")) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                Image(systemName: "person.circle.fill")
                    .foregroundColor(.gray)
            }
            .frame(width: 200, height: 200)
            .clipShape(Circle())
            .overlay(Circle().stroke(Color.divePrimary, lineWidth: 2))
            
            VStack(alignment: .leading, spacing: 4) {
                Text(instructor.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                // Certification Level
                if !instructor.credentials.isEmpty {
                    Text(instructor.credentials.first?.title ?? "")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                // Training Systems
                if !instructor.trainingSystems.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 4) {
                            ForEach(instructor.trainingSystems.prefix(3), id: \.self) { system in
                                Text(system)
                                    .font(.caption2)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.divePrimary.opacity(0.1))
                                    .foregroundColor(.divePrimary)
                                    .cornerRadius(4)
                            }
                        }
                    }
                }
                
                // Rating
                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                        .font(.caption)
                    Text(String(format: "%.1f", instructor.averageRating))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .frame(width: 200)
            
            Button(action: onBook) {
                Text(localizationService.localizedString("bookWithInstructor", table: "common"))
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(Color.divePrimary)
                    .cornerRadius(8)
            }
        }
        .padding()
        .background(Color.diveCard)
        .cornerRadius(12)
        .shadow(radius: 2)
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - Trip Card
struct TripCard: View {
    let trip: Trip
    let onTap: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var countryHelper = CountryLocalizationHelper.shared
    
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                // Trip Photo
                if let firstPhoto = trip.photos.first {
                    AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: firstPhoto) ?? "")) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle()
                            .fill(Color.gray.opacity(0.3))
                    }
                    .frame(width: 280, height: 160)
                    .clipped()
                    .cornerRadius(12)
                } else {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 280, height: 160)
                        .cornerRadius(12)
                        .overlay(
                            Image(systemName: "airplane.departure")
                                .font(.system(size: 40))
                                .foregroundColor(.gray)
                        )
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(countryHelper.getLocalizedCountryName(trip.country))
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    if let region = trip.region, !region.isEmpty {
                        Text(countryHelper.getLocalizedRegionName(region, countryName: trip.country))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    
                    Text("ui_admin_value_value_3".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    HStack {
                        Text("\(trip.availableSpots) \(localizationService.localizedString("spots", table: "trips"))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Spacer()
                    }
                    
                    // Show cheapest room/cabin price instead of general price
                    if let cheapestPrice = getCheapestPrice(for: trip) {
                        VStack(alignment: .leading, spacing: 2) {
                            if let divingPrice = cheapestPrice.divingPrice {
                                Text("\(localizationService.localizedString("forDivers", table: "trips")): \(formatPrice(divingPrice, currency: trip.priceDetails.currency))")
                                    .font(.caption)
                                    .foregroundColor(.divePrimary)
                            }
                            if let nonDivingPrice = cheapestPrice.nonDivingPrice {
                                Text("\(localizationService.localizedString("forNonDivers", table: "trips")): \(formatPrice(nonDivingPrice, currency: trip.priceDetails.currency))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
            }
            .frame(width: 280)
            .background(Color.diveCard)
            .cornerRadius(12)
            .shadow(radius: 2)
        }
        .buttonStyle(.plain)
        .task(id: localizationService.currentLanguage) {
            // Reload countries when language changes to update localized names
            await countryHelper.loadCountries()
        }
        .id(localizationService.currentLanguage)
    }
    
    private func formatPrice(_ amount: Double, currency: String) -> String {
        return String(format: "%.0f %@", amount, currency)
    }
    
    private func getCheapestPrice(for trip: Trip) -> (divingPrice: Double?, nonDivingPrice: Double?)? {
        if trip.tripType == .daily, let roomPrices = trip.priceDetails.roomPrices, !roomPrices.isEmpty {
            // Find cheapest room
            let cheapestRoom = roomPrices.min { room1, room2 in
                room1.divingPrice < room2.divingPrice
            }
            return (divingPrice: cheapestRoom?.divingPrice, nonDivingPrice: cheapestRoom?.nonDivingPrice)
        } else if trip.tripType == .safari, let yachtPrices = trip.priceDetails.yachtPrices, !yachtPrices.isEmpty {
            // Find cheapest cabin
            let cheapestCabin = yachtPrices.min { cabin1, cabin2 in
                cabin1.divingPrice < cabin2.divingPrice
            }
            return (divingPrice: cheapestCabin?.divingPrice, nonDivingPrice: cheapestCabin?.nonDivingPrice)
        }
        
        return nil
    }
}

// MARK: - Course Detail Public View
struct CourseDetailPublicView: View {
    let course: Course
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var courseEnrollmentViewModel = CourseViewModel()
    @State private var showEnrollment = false
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Course Photo Placeholder
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 200)
                        .overlay(
                            Image(systemName: "book.closed.fill")
                                .font(.system(size: 50))
                                .foregroundColor(.gray)
                        )
                    
                    VStack(alignment: .leading, spacing: 12) {
                        Text(course.name)
                            .font(.title)
                            .fontWeight(.bold)
                        
                        Text(course.displayDescription)
                            .font(.body)
                        
                        Divider()
                        
                        // Course Details
                        VStack(alignment: .leading, spacing: 8) {
                            DiveCenterInfoRow(icon: "clock", text: "\(course.duration) \(localizationService.localizedString("days", table: "common"))")
                            DiveCenterInfoRow(icon: "graduationcap", text: course.level.displayName)
                            
                            if !course.trainingSystems.isEmpty {
                                DiveCenterInfoRow(icon: "checkmark.seal", text: course.trainingSystems.joined(separator: ", "))
                            }
                            
                            if let prerequisites = course.prerequisites, !prerequisites.isEmpty {
                                DiveCenterInfoRow(icon: "list.bullet", text: "Prerequisites: \(prerequisites.joined(separator: ", "))")
                            }
                        }
                        
                        Divider()
                        
                        // Program
                        if !course.program.isEmpty {
                            Text(localizationService.localizedString("program", table: "trips"))
                                .font(.headline)
                            
                            ForEach(course.program.sorted(by: { $0.order < $1.order })) { module in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(module.title)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    Text(module.displayDescription)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                .padding()
                                .background(Color.diveBackground)
                                .cornerRadius(8)
                            }
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle(course.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("done", table: "common")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("enroll", table: "courses")) {
                        showEnrollment = true
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .sheet(isPresented: $showEnrollment) {
                CourseBookingView(course: course, courseViewModel: courseEnrollmentViewModel)
            }
        }
    }
}

// MARK: - Instructor Courses View
struct InstructorCoursesView: View {
    let courses: [Course]
    let instructor: Instructor?
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedCourse: Course?
    
    var body: some View {
        List {
            if let instructor = instructor {
                Section {
                    HStack {
                        AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: instructor.photoURL ?? instructor.avatarURL) ?? "")) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Image(systemName: "person.circle.fill")
                        }
                        .frame(width: 60, height: 60)
                        .clipShape(Circle())
                        
                        VStack(alignment: .leading) {
                            Text(instructor.name)
                                .font(.headline)
                            Text("\(courses.count) \(localizationService.localizedString("courses", table: "courses"))")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            
            Section {
                ForEach(courses) { course in
                    Button(action: {
                        selectedCourse = course
                    }) {
                        CourseListRow(course: course)
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("courses", table: "courses"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(localizationService.localizedString("done", table: "common")) {
                    dismiss()
                }
            }
        }
        .sheet(item: $selectedCourse) { course in
            CourseDetailPublicView(course: course)
        }
    }
}

// MARK: - Course List Row
struct CourseListRow: View {
    let course: Course
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(course.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                Text(course.displayDescription)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
            
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Info Row
struct DiveCenterInfoRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(.divePrimary)
                .frame(width: 20)
            Text(text)
                .font(.body)
        }
    }
}

#Preview {
    NavigationView {
        DiveCenterPublicView(center: DiveCenter(
            id: "1",
            name: "Blue Water Diving",
            description: "A great dive center",
            location: DiveCenter.Location(
                latitude: 20.0,
                longitude: -80.0,
                address: "123 Beach Road",
                city: "Cozumel",
                country: "Mexico"
            ),
            contactInfo: DiveCenter.ContactInfo(
                phone: "+52 987 123 4567",
                email: "info@bluewater.com",
                website: nil,
                socialMedia: nil
            ),
            photos: [],
            videos: [],
            averageRating: 4.8,
            reviewCount: 156,
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

// Separate view component for photo loading to avoid SwiftUI issues with let inside ForEach
struct PhotoView: View {
    let photoURL: String
    
    var body: some View {
        AsyncImage(url: URL(string: NetworkService.shared.fullImageURL(from: photoURL) ?? "")) { image in
            image
                .resizable()
                .aspectRatio(contentMode: .fill)
                .clipped()
        } placeholder: {
            Rectangle()
                .fill(Color.gray.opacity(0.3))
        }
    }
}
