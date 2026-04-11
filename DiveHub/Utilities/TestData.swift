//
//  TestData.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation

#if DEBUG
struct TestData {
    // MARK: - Test Dive Center IDs
    static let testDiveCenterId = "test-dive-center-1"
    static let testInstructorId = "test-instructor-1"
    static let testUserId1 = "test-user-1"
    static let testUserId2 = "test-user-2"
    static let testUserId3 = "test-user-3"
    
    // MARK: - Test Bookings for Instructors
    static var instructorBookings: [Booking] {
        let calendar = Calendar.current
        let today = Date()
        
        return [
            // Today's bookings
            Booking(
                id: "booking-instructor-1",
                userId: testUserId1,
                diveCenterId: testDiveCenterId,
                serviceId: "service-1",
                diveSiteId: "dive-site-1",
                instructorId: testInstructorId,
                date: today,
                startTime: "09:00",
                participants: [
                    Booking.Participant(
                        id: "participant-1",
                        name: "John Smith",
                        email: "john.smith@example.com",
                        phoneNumber: "+1234567890",
                        certificationLevel: "Open Water",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: [
                    Booking.GearRental(
                        id: "gear-rental-1",
                        gearItemId: "gear-1",
                        gearName: "Wetsuit",
                        size: "M",
                        quantity: 1,
                        price: 25.0
                    )
                ],
                payment: Booking.Payment(
                    method: .online,
                    amount: 150.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-001",
                    paidAt: today.addingTimeInterval(-86400)
                ),
                status: .confirmed,
                notes: "First time diver, needs extra attention",
                createdAt: today.addingTimeInterval(-172800),
                updatedAt: today.addingTimeInterval(-86400)
            ),
            Booking(
                id: "booking-instructor-2",
                userId: testUserId2,
                diveCenterId: testDiveCenterId,
                serviceId: "service-2",
                diveSiteId: "dive-site-2",
                instructorId: testInstructorId,
                date: today,
                startTime: "14:00",
                participants: [
                    Booking.Participant(
                        id: "participant-2",
                        name: "Maria Garcia",
                        email: "maria.garcia@example.com",
                        phoneNumber: "+1234567891",
                        certificationLevel: "Advanced Open Water",
                        isFriend: false,
                        friendUserId: nil
                    ),
                    Booking.Participant(
                        id: "participant-3",
                        name: "Carlos Garcia",
                        email: "carlos.garcia@example.com",
                        phoneNumber: "+1234567892",
                        certificationLevel: "Advanced Open Water",
                        isFriend: true,
                        friendUserId: testUserId3
                    )
                ],
                gearRental: nil,
                payment: Booking.Payment(
                    method: .applePay,
                    amount: 280.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-002",
                    paidAt: today.addingTimeInterval(-86400)
                ),
                status: .confirmed,
                notes: "Couple diving together",
                createdAt: today.addingTimeInterval(-172800),
                updatedAt: today.addingTimeInterval(-86400)
            ),
            // Upcoming bookings
            Booking(
                id: "booking-instructor-3",
                userId: testUserId3,
                diveCenterId: testDiveCenterId,
                serviceId: "service-3",
                diveSiteId: "dive-site-3",
                instructorId: testInstructorId,
                date: calendar.date(byAdding: .day, value: 1, to: today) ?? today,
                startTime: "10:00",
                participants: [
                    Booking.Participant(
                        id: "participant-4",
                        name: "David Lee",
                        email: "david.lee@example.com",
                        phoneNumber: "+1234567893",
                        certificationLevel: "Rescue Diver",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: [
                    Booking.GearRental(
                        id: "gear-rental-2",
                        gearItemId: "gear-2",
                        gearName: "BCD",
                        size: "L",
                        quantity: 1,
                        price: 30.0
                    ),
                    Booking.GearRental(
                        id: "gear-rental-3",
                        gearItemId: "gear-3",
                        gearName: "Regulator",
                        size: "One Size",
                        quantity: 1,
                        price: 35.0
                    )
                ],
                payment: Booking.Payment(
                    method: .online,
                    amount: 200.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-003",
                    paidAt: today.addingTimeInterval(-86400)
                ),
                status: .confirmed,
                notes: nil,
                createdAt: today.addingTimeInterval(-259200),
                updatedAt: today.addingTimeInterval(-172800)
            ),
            Booking(
                id: "booking-instructor-4",
                userId: testUserId1,
                diveCenterId: testDiveCenterId,
                serviceId: "service-4",
                diveSiteId: "dive-site-1",
                instructorId: testInstructorId,
                date: calendar.date(byAdding: .day, value: 2, to: today) ?? today,
                startTime: "08:00",
                participants: [
                    Booking.Participant(
                        id: "participant-5",
                        name: "Sarah Johnson",
                        email: "sarah.johnson@example.com",
                        phoneNumber: "+1234567894",
                        certificationLevel: "Open Water",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: nil,
                payment: Booking.Payment(
                    method: .onSite,
                    amount: 120.0,
                    currency: "USD",
                    status: .pending,
                    transactionId: nil,
                    paidAt: nil
                ),
                status: .pending,
                notes: "Will pay on site",
                createdAt: today.addingTimeInterval(-86400),
                updatedAt: today.addingTimeInterval(-86400)
            ),
            Booking(
                id: "booking-instructor-5",
                userId: testUserId2,
                diveCenterId: testDiveCenterId,
                serviceId: "service-5",
                diveSiteId: "dive-site-2",
                instructorId: testInstructorId,
                date: calendar.date(byAdding: .day, value: 3, to: today) ?? today,
                startTime: "11:00",
                participants: [
                    Booking.Participant(
                        id: "participant-6",
                        name: "Michael Brown",
                        email: "michael.brown@example.com",
                        phoneNumber: "+1234567895",
                        certificationLevel: "Divemaster",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: [
                    Booking.GearRental(
                        id: "gear-rental-4",
                        gearItemId: "gear-4",
                        gearName: "Dive Computer",
                        size: "One Size",
                        quantity: 1,
                        price: 40.0
                    )
                ],
                payment: Booking.Payment(
                    method: .online,
                    amount: 180.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-004",
                    paidAt: today.addingTimeInterval(-43200)
                ),
                status: .confirmed,
                notes: "Experienced diver",
                createdAt: today.addingTimeInterval(-172800),
                updatedAt: today.addingTimeInterval(-43200)
            ),
            // Completed bookings
            Booking(
                id: "booking-instructor-6",
                userId: testUserId3,
                diveCenterId: testDiveCenterId,
                serviceId: "service-6",
                diveSiteId: "dive-site-3",
                instructorId: testInstructorId,
                date: calendar.date(byAdding: .day, value: -1, to: today) ?? today,
                startTime: "13:00",
                participants: [
                    Booking.Participant(
                        id: "participant-7",
                        name: "Emma Wilson",
                        email: "emma.wilson@example.com",
                        phoneNumber: "+1234567896",
                        certificationLevel: "Open Water",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: nil,
                payment: Booking.Payment(
                    method: .online,
                    amount: 140.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-005",
                    paidAt: calendar.date(byAdding: .day, value: -2, to: today) ?? today
                ),
                status: .completed,
                notes: "Great dive!",
                createdAt: calendar.date(byAdding: .day, value: -3, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -1, to: today) ?? today
            ),
            // Cancelled booking
            Booking(
                id: "booking-instructor-7",
                userId: testUserId1,
                diveCenterId: testDiveCenterId,
                serviceId: "service-7",
                diveSiteId: "dive-site-1",
                instructorId: testInstructorId,
                date: calendar.date(byAdding: .day, value: -2, to: today) ?? today,
                startTime: "15:00",
                participants: [
                    Booking.Participant(
                        id: "participant-8",
                        name: "Robert Taylor",
                        email: "robert.taylor@example.com",
                        phoneNumber: "+1234567897",
                        certificationLevel: "Advanced Open Water",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: nil,
                payment: Booking.Payment(
                    method: .online,
                    amount: 160.0,
                    currency: "USD",
                    status: .refunded,
                    transactionId: "txn-006",
                    paidAt: calendar.date(byAdding: .day, value: -5, to: today) ?? today
                ),
                status: .cancelled,
                notes: "Cancelled due to weather",
                createdAt: calendar.date(byAdding: .day, value: -7, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -2, to: today) ?? today
            )
        ]
    }
    
    // MARK: - Test Bookings for Dive Center Admins
    static var adminBookings: [Booking] {
        var bookings = instructorBookings
        // Add more bookings for admin view
        let calendar = Calendar.current
        let today = Date()
        
        bookings.append(contentsOf: [
            Booking(
                id: "booking-admin-1",
                userId: testUserId2,
                diveCenterId: testDiveCenterId,
                serviceId: "service-8",
                diveSiteId: "dive-site-2",
                instructorId: "test-instructor-2",
                date: calendar.date(byAdding: .day, value: 4, to: today) ?? today,
                startTime: "09:30",
                participants: [
                    Booking.Participant(
                        id: "participant-9",
                        name: "Lisa Anderson",
                        email: "lisa.anderson@example.com",
                        phoneNumber: "+1234567898",
                        certificationLevel: "Open Water",
                        isFriend: false,
                        friendUserId: nil
                    ),
                    Booking.Participant(
                        id: "participant-10",
                        name: "Tom Anderson",
                        email: "tom.anderson@example.com",
                        phoneNumber: "+1234567899",
                        certificationLevel: "Open Water",
                        isFriend: true,
                        friendUserId: testUserId2
                    )
                ],
                gearRental: [
                    Booking.GearRental(
                        id: "gear-rental-5",
                        gearItemId: "gear-5",
                        gearName: "Full Set",
                        size: "M",
                        quantity: 2,
                        price: 100.0
                    )
                ],
                payment: Booking.Payment(
                    method: .googlePay,
                    amount: 300.0,
                    currency: "USD",
                    status: .paid,
                    transactionId: "txn-007",
                    paidAt: today.addingTimeInterval(-3600)
                ),
                status: .confirmed,
                notes: "Family booking",
                createdAt: today.addingTimeInterval(-259200),
                updatedAt: today.addingTimeInterval(-3600)
            ),
            Booking(
                id: "booking-admin-2",
                userId: testUserId3,
                diveCenterId: testDiveCenterId,
                serviceId: "service-9",
                diveSiteId: "dive-site-3",
                instructorId: "test-instructor-3",
                date: calendar.date(byAdding: .day, value: 5, to: today) ?? today,
                startTime: "16:00",
                participants: [
                    Booking.Participant(
                        id: "participant-11",
                        name: "Jennifer White",
                        email: "jennifer.white@example.com",
                        phoneNumber: "+1234567900",
                        certificationLevel: "Advanced Open Water",
                        isFriend: false,
                        friendUserId: nil
                    )
                ],
                gearRental: nil,
                payment: Booking.Payment(
                    method: .online,
                    amount: 170.0,
                    currency: "USD",
                    status: .pending,
                    transactionId: nil,
                    paidAt: nil
                ),
                status: .pending,
                notes: nil,
                createdAt: today.addingTimeInterval(-172800),
                updatedAt: today.addingTimeInterval(-172800)
            )
        ])
        
        return bookings
    }
    
    // MARK: - Test Gear Items
    static var gearItems: [GearItem] {
        let today = Date()
        let calendar = Calendar.current
        
        return [
            GearItem(
                id: "gear-1",
                diveCenterId: testDiveCenterId,
                name: "Wetsuit 3mm",
                description: "High-quality neoprene wetsuit, perfect for warm water diving",
                category: .wetsuit,
                manufacturer: "Scubapro",
                model: "Everflex 3/2",
                sizes: ["XS", "S", "M", "L", "XL"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 25.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -2, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 4, to: today),
                    serviceHistory: [],
                    notes: "In good condition"
                ),
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-2",
                diveCenterId: testDiveCenterId,
                name: "BCD Jacket",
                description: "Adjustable BCD with integrated weight system",
                category: .bcd,
                manufacturer: "Cressi",
                model: "Travelight",
                sizes: ["XS", "S", "M", "L", "XL"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 30.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -1, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 5, to: today),
                    serviceHistory: [],
                    notes: nil
                ),
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-3",
                diveCenterId: testDiveCenterId,
                name: "Regulator Set",
                description: "Complete regulator set with octopus and pressure gauge",
                category: .regulator,
                manufacturer: "Aqualung",
                model: "Legend Elite",
                sizes: ["One Size"],
                photos: [],
                status: .issued,
                rentalPrice: GearItem.Price(amount: 35.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -3, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 3, to: today),
                    serviceHistory: [
                        GearItem.MaintenanceInfo.ServiceRecord(
                            id: "service-1",
                            date: calendar.date(byAdding: .month, value: -3, to: today) ?? today,
                            type: .inspection,
                            description: "Annual service and inspection",
                            performedBy: "Service Center"
                        )
                    ],
                    notes: "Currently rented"
                ),
                createdAt: calendar.date(byAdding: .year, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-4",
                diveCenterId: testDiveCenterId,
                name: "Dive Computer",
                description: "Advanced dive computer with air integration",
                category: .computer,
                manufacturer: "Suunto",
                model: "D5",
                sizes: ["One Size"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 40.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -1, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 11, to: today),
                    serviceHistory: [],
                    notes: "Battery replaced recently"
                ),
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-5",
                diveCenterId: testDiveCenterId,
                name: "Fins",
                description: "Open heel fins with adjustable straps",
                category: .fins,
                manufacturer: "Mares",
                model: "Avanti Quattro",
                sizes: ["XS", "S", "M", "L", "XL"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 15.0, currency: "USD", period: .perDive),
                maintenance: nil,
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-6",
                diveCenterId: testDiveCenterId,
                name: "Mask & Snorkel Set",
                description: "Silicone mask with dry-top snorkel",
                category: .mask,
                manufacturer: "Cressi",
                model: "F1 Frameless",
                sizes: ["One Size"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 10.0, currency: "USD", period: .perDive),
                maintenance: nil,
                createdAt: calendar.date(byAdding: .month, value: -6, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-7",
                diveCenterId: testDiveCenterId,
                name: "Wetsuit 5mm",
                description: "Thicker wetsuit for colder water",
                category: .wetsuit,
                manufacturer: "Scubapro",
                model: "Everflex 5/4",
                sizes: ["S", "M", "L", "XL"],
                photos: [],
                status: .maintenance,
                rentalPrice: GearItem.Price(amount: 30.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .day, value: -5, to: today),
                    nextServiceDate: calendar.date(byAdding: .day, value: 2, to: today),
                    serviceHistory: [
                        GearItem.MaintenanceInfo.ServiceRecord(
                            id: "service-2",
                            date: calendar.date(byAdding: .day, value: -5, to: today) ?? today,
                            type: .repair,
                            description: "Zipper replacement",
                            performedBy: "In-house technician"
                        )
                    ],
                    notes: "Awaiting zipper replacement"
                ),
                createdAt: calendar.date(byAdding: .year, value: -2, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -5, to: today) ?? today
            ),
            GearItem(
                id: "gear-8",
                diveCenterId: testDiveCenterId,
                name: "BCD Back-Inflate",
                description: "Back-inflate BCD for experienced divers",
                category: .bcd,
                manufacturer: "Aqualung",
                model: "Dimension",
                sizes: ["M", "L", "XL"],
                photos: [],
                status: .issued,
                rentalPrice: GearItem.Price(amount: 32.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -2, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 4, to: today),
                    serviceHistory: [],
                    notes: "Currently rented"
                ),
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-9",
                diveCenterId: testDiveCenterId,
                name: "Weight Belt",
                description: "Nylon weight belt with quick release",
                category: .weight,
                manufacturer: "Generic",
                model: nil,
                sizes: ["S", "M", "L"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 5.0, currency: "USD", period: .perDive),
                maintenance: nil,
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-10",
                diveCenterId: testDiveCenterId,
                name: "Underwater Camera",
                description: "GoPro Hero 10 with underwater housing",
                category: .camera,
                manufacturer: "GoPro",
                model: "Hero 10",
                sizes: ["One Size"],
                photos: [],
                status: .available,
                rentalPrice: GearItem.Price(amount: 50.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .month, value: -1, to: today),
                    nextServiceDate: calendar.date(byAdding: .month, value: 5, to: today),
                    serviceHistory: [],
                    notes: "Includes memory card"
                ),
                createdAt: calendar.date(byAdding: .month, value: -6, to: today) ?? today,
                updatedAt: today
            ),
            GearItem(
                id: "gear-11",
                diveCenterId: testDiveCenterId,
                name: "Old Regulator",
                description: "Outdated regulator, needs replacement",
                category: .regulator,
                manufacturer: "Unknown",
                model: "Old Model",
                sizes: ["One Size"],
                photos: [],
                status: .scrapped,
                rentalPrice: GearItem.Price(amount: 0.0, currency: "USD", period: .perDive),
                maintenance: GearItem.MaintenanceInfo(
                    lastServiceDate: calendar.date(byAdding: .year, value: -3, to: today),
                    nextServiceDate: nil,
                    serviceHistory: [
                        GearItem.MaintenanceInfo.ServiceRecord(
                            id: "service-3",
                            date: calendar.date(byAdding: .year, value: -3, to: today) ?? today,
                            type: .inspection,
                            description: "Failed inspection, beyond repair",
                            performedBy: "Service Center"
                        )
                    ],
                    notes: "Marked for disposal"
                ),
                createdAt: calendar.date(byAdding: .year, value: -5, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today
            )
        ]
    }
    
    // MARK: - Test Instructors (Users)
    static var instructors: [User] {
        let today = Date()
        
        return [
            User(
                id: testInstructorId,
                email: "instructor1@divecenter.com",
                phoneNumber: "+1234567001",
                firstName: "Alex",
                lastName: "Martinez",
                avatarURL: nil,
                role: .instructor,
                subscriptionStatus: nil,
                subscriptionExpiresAt: nil,
                certificationLevel: "PADI Master Instructor",
                diveCenterId: testDiveCenterId,
                language: "en",
                createdAt: calendar.date(byAdding: .year, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            User(
                id: "test-instructor-2",
                email: "instructor2@divecenter.com",
                phoneNumber: "+1234567002",
                firstName: "Sarah",
                lastName: "Johnson",
                avatarURL: nil,
                role: .instructor,
                subscriptionStatus: nil,
                subscriptionExpiresAt: nil,
                certificationLevel: "SSI Instructor Trainer",
                diveCenterId: testDiveCenterId,
                language: "en",
                createdAt: calendar.date(byAdding: .year, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            User(
                id: "test-instructor-3",
                email: "instructor3@divecenter.com",
                phoneNumber: "+1234567003",
                firstName: "Michael",
                lastName: "Chen",
                avatarURL: nil,
                role: .instructor,
                subscriptionStatus: nil,
                subscriptionExpiresAt: nil,
                certificationLevel: "PADI Course Director",
                diveCenterId: testDiveCenterId,
                language: "en",
                createdAt: calendar.date(byAdding: .year, value: -3, to: today) ?? today,
                updatedAt: today
            ),
            User(
                id: "test-instructor-4",
                email: "instructor4@divecenter.com",
                phoneNumber: "+1234567004",
                firstName: "Emma",
                lastName: "Wilson",
                avatarURL: nil,
                role: .instructor,
                subscriptionStatus: nil,
                subscriptionExpiresAt: nil,
                certificationLevel: "NAUI Instructor",
                diveCenterId: testDiveCenterId,
                language: "en",
                createdAt: calendar.date(byAdding: .month, value: -6, to: today) ?? today,
                updatedAt: today
            )
        ]
    }
    
    // MARK: - Test Courses for Dive Center
    static var testCourses: [Course] {
        let today = Date()
        let calendar = Calendar.current
        
        return [
            Course(
                id: "course-1",
                name: "Open Water Diver",
                level: .basic,
                description: "Learn the fundamentals of scuba diving. This course covers basic diving skills, safety procedures, and underwater navigation.",
                trainingSystems: ["PADI", "SSI"],
                program: [
                    Course.CourseModule(
                        id: "module-1",
                        title: "Introduction to Scuba Diving",
                        description: "Learn about equipment and basic concepts",
                        duration: 2,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-2",
                        title: "Confined Water Training",
                        description: "Practice basic skills in pool",
                        duration: 4,
                        moduleType: .confinedWater,
                        order: 2
                    ),
                    Course.CourseModule(
                        id: "module-3",
                        title: "Open Water Dives",
                        description: "4 open water dives to complete certification",
                        duration: 8,
                        moduleType: .openWater,
                        order: 3
                    )
                ],
                duration: 3,
                prerequisites: nil,
                diveCenterId: testDiveCenterId,
                instructorId: testInstructorId,
                photos: ["https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -6, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-2",
                name: "Advanced Open Water Diver",
                level: .advanced,
                description: "Expand your diving skills with 5 adventure dives including deep diving and navigation.",
                trainingSystems: ["PADI", "SSI"],
                program: [
                    Course.CourseModule(
                        id: "module-4",
                        title: "Deep Diving",
                        description: "Learn to dive deeper safely",
                        duration: 2,
                        moduleType: .openWater,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-5",
                        title: "Underwater Navigation",
                        description: "Master compass and natural navigation",
                        duration: 2,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 2,
                prerequisites: ["Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: "test-instructor-2",
                photos: ["https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -5, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-3",
                name: "Rescue Diver",
                level: .advanced,
                description: "Learn to prevent and manage problems in the water, and become more confident in your skills as a diver.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-6",
                        title: "Self Rescue",
                        description: "Learn to handle your own problems",
                        duration: 3,
                        moduleType: .openWater,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-7",
                        title: "Rescuing Others",
                        description: "Learn to assist other divers in distress",
                        duration: 4,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 3,
                prerequisites: ["Advanced Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: "test-instructor-3",
                photos: ["https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -4, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-4",
                name: "Divemaster",
                level: .professional,
                description: "The first professional level certification. Learn to lead dives and assist instructors.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-8",
                        title: "Leadership Skills",
                        description: "Learn to lead dives safely",
                        duration: 20,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-9",
                        title: "Practical Training",
                        description: "Assist with courses and lead dives",
                        duration: 40,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 14,
                prerequisites: ["Rescue Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: nil,
                photos: ["https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -3, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-5",
                name: "Nitrox Diver",
                level: .specialization,
                description: "Learn to dive with enriched air nitrox for longer bottom times.",
                trainingSystems: ["PADI", "SSI"],
                program: [
                    Course.CourseModule(
                        id: "module-10",
                        title: "Nitrox Theory",
                        description: "Understanding enriched air",
                        duration: 2,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-11",
                        title: "Nitrox Dives",
                        description: "Practice diving with nitrox",
                        duration: 2,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 1,
                prerequisites: ["Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: nil,
                photos: ["https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-6",
                name: "Deep Diver",
                level: .specialization,
                description: "Learn to dive deeper than 18 meters safely and confidently.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-12",
                        title: "Deep Diving Theory",
                        description: "Understanding deep diving physics and safety",
                        duration: 2,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-13",
                        title: "Deep Dives",
                        description: "4 deep dives to 40 meters",
                        duration: 4,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 2,
                prerequisites: ["Advanced Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: nil,
                photos: ["https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-7",
                name: "Wreck Diver",
                level: .specialization,
                description: "Explore sunken ships and aircraft safely. Learn wreck penetration techniques.",
                trainingSystems: ["PADI", "SSI"],
                program: [
                    Course.CourseModule(
                        id: "module-14",
                        title: "Wreck Diving Safety",
                        description: "Safety procedures for wreck diving",
                        duration: 2,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-15",
                        title: "Wreck Dives",
                        description: "4 wreck dives with penetration",
                        duration: 4,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 2,
                prerequisites: ["Advanced Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: nil,
                photos: ["https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-8",
                name: "Night Diver",
                level: .specialization,
                description: "Experience the underwater world after dark. Learn night diving techniques and safety.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-16",
                        title: "Night Diving Theory",
                        description: "Understanding night diving procedures",
                        duration: 1,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-17",
                        title: "Night Dives",
                        description: "3 night dives",
                        duration: 3,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 1,
                prerequisites: ["Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: "test-instructor-4",
                photos: ["https://images.unsplash.com/photo-1583212292454-1fe6229603b7?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-9",
                name: "Underwater Photography",
                level: .specialization,
                description: "Capture amazing underwater moments. Learn photography techniques and equipment handling.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-18",
                        title: "Photography Basics",
                        description: "Understanding underwater photography",
                        duration: 2,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-19",
                        title: "Underwater Photo Dives",
                        description: "Practice photography during dives",
                        duration: 4,
                        moduleType: .openWater,
                        order: 2
                    )
                ],
                duration: 2,
                prerequisites: ["Open Water Diver"],
                diveCenterId: testDiveCenterId,
                instructorId: "test-instructor-2",
                photos: ["https://images.unsplash.com/photo-1559827260-dc66d52bef19?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            Course(
                id: "course-10",
                name: "Instructor Development Course",
                level: .professional,
                description: "Become a PADI Instructor. Learn to teach diving courses and certify new divers.",
                trainingSystems: ["PADI"],
                program: [
                    Course.CourseModule(
                        id: "module-20",
                        title: "Teaching Theory",
                        description: "Learn teaching methods and techniques",
                        duration: 40,
                        moduleType: .theory,
                        order: 1
                    ),
                    Course.CourseModule(
                        id: "module-21",
                        title: "Practical Teaching",
                        description: "Teach under supervision",
                        duration: 60,
                        moduleType: .openWater,
                        order: 2
                    ),
                    Course.CourseModule(
                        id: "module-22",
                        title: "Instructor Exam",
                        description: "Final examination",
                        duration: 2,
                        moduleType: .exam,
                        order: 3
                    )
                ],
                duration: 10,
                prerequisites: ["Divemaster"],
                diveCenterId: testDiveCenterId,
                instructorId: nil,
                photos: ["https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800"],
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            )
        ]
    }
    
    // MARK: - Test Trips for Dive Center
    static var testTrips: [Trip] {
        let today = Date()
        let calendar = Calendar.current
        
        return [
            Trip(
                id: "trip-1",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-1",
                yachtId: nil as String?,
                country: "Egypt",
                startDate: calendar.date(byAdding: .day, value: 30, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 37, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 10,
                description: "Week-long diving trip to Red Sea. Explore amazing coral reefs and diverse marine life.",
                photos: [],
                totalSpots: 12,
                bookedSpots: 3,
                participants: [],
                availableCourses: ["course-1", "course-2", "course-5"],
                nitroxAvailable: true,
                groupLeaderId: testInstructorId,
                program: [
                    Trip.TripProgramDay(
                        id: "day-1",
                        date: calendar.date(byAdding: .day, value: 30, to: today) ?? today,
                        activities: [
                            Trip.TripProgramDay.ProgramActivity(
                                id: "activity-1",
                                time: "09:00",
                                activity: "Check-in and equipment setup",
                                diveSiteId: nil,
                                diveCenterId: nil,
                                notes: nil
                            ),
                            Trip.TripProgramDay.ProgramActivity(
                                id: "activity-2",
                                time: "14:00",
                                activity: "First dive - House Reef",
                                diveSiteId: "dive-site-1",
                                diveCenterId: nil,
                                notes: "Easy dive for check-out"
                            )
                        ],
                        description: "Arrival and first dive"
                    )
                ],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-1",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 500,
                        currency: "USD"
                    ),
                    Trip.AdditionalExpense(
                        id: "expense-2",
                        expenseType: .transfer,
                        description: "Airport transfer",
                        cost: 50,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-1",
                            roomType: "Single",
                            roomCount: 5,
                            divingPrice: 1200,
                            nonDivingPrice: 800
                        ),
                        Trip.PriceDetails.RoomPrice(
                            id: "room-2",
                            roomType: "Double",
                            roomCount: 10,
                            divingPrice: 1000,
                            nonDivingPrice: 700
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            Trip(
                id: "trip-2",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.safari,
                hotelId: nil as String?,
                yachtId: "yacht-1",
                country: "Maldives",
                startDate: calendar.date(byAdding: .day, value: 60, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 67, to: today) ?? today,
                minimumCertificationLevel: "Advanced Open Water",
                minimumDives: 50,
                description: "Liveaboard safari in Maldives. Experience world-class diving with manta rays and whale sharks.",
                photos: [],
                totalSpots: 16,
                bookedSpots: 8,
                participants: [],
                availableCourses: ["course-2", "course-3", "course-6"],
                nitroxAvailable: true,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-3",
                        expenseType: .flight,
                        description: "Round trip flight to Male",
                        cost: 800,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: false,
                priceDetails: Trip.PriceDetails(
                    roomPrices: nil,
                    yachtPrices: [
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-1",
                            cabinType: "Standard",
                            cabinCount: 8,
                            divingPrice: 2500,
                            nonDivingPrice: 1800
                        ),
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-2",
                            cabinType: "Deluxe",
                            cabinCount: 4,
                            divingPrice: 3000,
                            nonDivingPrice: 2200
                        )
                    ],
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            Trip(
                id: "trip-3",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-2",
                yachtId: nil as String?,
                country: "Thailand",
                startDate: calendar.date(byAdding: .day, value: 90, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 97, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 5,
                description: "Diving adventure in Phuket. Explore beautiful reefs and enjoy Thai hospitality.",
                photos: [],
                totalSpots: 10,
                bookedSpots: 2,
                participants: [],
                availableCourses: ["course-1", "course-5", "course-8"],
                nitroxAvailable: false,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-4",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 600,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-3",
                            roomType: "Double",
                            roomCount: 8,
                            divingPrice: 900,
                            nonDivingPrice: 600
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            Trip(
                id: "trip-4",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.safari,
                hotelId: nil as String?,
                yachtId: "yacht-2",
                country: "Indonesia",
                startDate: calendar.date(byAdding: .day, value: 120, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 130, to: today) ?? today,
                minimumCertificationLevel: "Advanced Open Water",
                minimumDives: 30,
                description: "10-day liveaboard in Komodo. Dive with dragons and explore pristine reefs.",
                photos: [],
                totalSpots: 20,
                bookedSpots: 12,
                participants: [],
                availableCourses: ["course-2", "course-7"],
                nitroxAvailable: true,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-5",
                        expenseType: .flight,
                        description: "Round trip flight to Bali",
                        cost: 700,
                        currency: "USD"
                    ),
                    Trip.AdditionalExpense(
                        id: "expense-6",
                        expenseType: .reserve,
                        description: "Komodo National Park fee",
                        cost: 100,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: false,
                priceDetails: Trip.PriceDetails(
                    roomPrices: nil,
                    yachtPrices: [
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-3",
                            cabinType: "Standard",
                            cabinCount: 6,
                            divingPrice: 2200,
                            nonDivingPrice: 1600
                        )
                    ],
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            Trip(
                id: "trip-5",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-3",
                yachtId: nil as String?,
                country: "Philippines",
                startDate: calendar.date(byAdding: .day, value: 150, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 157, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 10,
                description: "Diving in Palawan. Experience world-famous Tubbataha Reef and amazing biodiversity.",
                photos: [],
                totalSpots: 14,
                bookedSpots: 5,
                participants: [],
                availableCourses: ["course-1", "course-2", "course-9"],
                nitroxAvailable: true,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-7",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 650,
                        currency: "USD"
                    ),
                    Trip.AdditionalExpense(
                        id: "expense-8",
                        expenseType: .nutrition,
                        description: "Full board meals",
                        cost: 200,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-4",
                            roomType: "Single",
                            roomCount: 6,
                            divingPrice: 1100,
                            nonDivingPrice: 750
                        ),
                        Trip.PriceDetails.RoomPrice(
                            id: "room-5",
                            roomType: "Double",
                            roomCount: 12,
                            divingPrice: 950,
                            nonDivingPrice: 650
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -3, to: today) ?? today,
                updatedAt: today
            ),
            // Trip 6 - Past (Archived)
            Trip(
                id: "trip-6",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-4",
                yachtId: nil as String?,
                country: "Mexico",
                region: "Cozumel",
                startDate: calendar.date(byAdding: .day, value: -60, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: -53, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 5,
                description: "Amazing diving in Cozumel. Explore the famous Palancar Reef and vibrant coral walls.",
                photos: [],
                totalSpots: 10,
                bookedSpots: 10,
                participants: [],
                availableCourses: ["course-1", "course-5"],
                nitroxAvailable: true,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-9",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 450,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-6",
                            roomType: "Double",
                            roomCount: 8,
                            divingPrice: 800,
                            nonDivingPrice: 550
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -6, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -53, to: today) ?? today
            ),
            // Trip 7 - Past (Archived)
            Trip(
                id: "trip-7",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.safari,
                hotelId: nil as String?,
                yachtId: "yacht-3",
                country: "Red Sea",
                region: "Egypt",
                startDate: calendar.date(byAdding: .day, value: -90, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: -80, to: today) ?? today,
                minimumCertificationLevel: "Advanced Open Water",
                minimumDives: 30,
                description: "Liveaboard safari in the Red Sea. Dive world-class sites including the Brothers and Daedalus Reef.",
                photos: [],
                totalSpots: 16,
                bookedSpots: 16,
                participants: [],
                availableCourses: ["course-2", "course-6"],
                nitroxAvailable: true,
                groupLeaderId: "test-instructor-2",
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-10",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 600,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: nil,
                    yachtPrices: [
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-3",
                            cabinType: "Standard",
                            cabinCount: 8,
                            divingPrice: 1200,
                            nonDivingPrice: 800
                        ),
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-4",
                            cabinType: "Deluxe",
                            cabinCount: 4,
                            divingPrice: 1500,
                            nonDivingPrice: 1000
                        )
                    ],
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -8, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -80, to: today) ?? today
            ),
            // Trip 8 - Future (Upcoming)
            Trip(
                id: "trip-8",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-5",
                yachtId: nil as String?,
                country: "Croatia",
                region: "Dalmatia",
                startDate: calendar.date(byAdding: .day, value: 180, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 187, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 10,
                description: "Discover the Adriatic Sea. Beautiful clear waters, diverse marine life, and historic shipwrecks.",
                photos: [],
                totalSpots: 12,
                bookedSpots: 3,
                participants: [],
                availableCourses: ["course-1", "course-7"],
                nitroxAvailable: false,
                groupLeaderId: "test-instructor-3",
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-11",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 550,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-7",
                            roomType: "Single",
                            roomCount: 4,
                            divingPrice: 900,
                            nonDivingPrice: 600
                        ),
                        Trip.PriceDetails.RoomPrice(
                            id: "room-8",
                            roomType: "Double",
                            roomCount: 8,
                            divingPrice: 750,
                            nonDivingPrice: 500
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -2, to: today) ?? today,
                updatedAt: today
            ),
            // Trip 9 - Future (Upcoming)
            Trip(
                id: "trip-9",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.safari,
                hotelId: nil as String?,
                yachtId: "yacht-4",
                country: "Malaysia",
                region: "Sipadan",
                startDate: calendar.date(byAdding: .day, value: 210, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: 220, to: today) ?? today,
                minimumCertificationLevel: "Advanced Open Water",
                minimumDives: 50,
                description: "World-famous Sipadan Island. Experience incredible biodiversity, turtles, sharks, and pristine reefs.",
                photos: [],
                totalSpots: 14,
                bookedSpots: 7,
                participants: [],
                availableCourses: ["course-2", "course-6", "course-9"],
                nitroxAvailable: true,
                groupLeaderId: "test-instructor-4",
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-12",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 750,
                        currency: "USD"
                    ),
                    Trip.AdditionalExpense(
                        id: "expense-13",
                        expenseType: .transfer,
                        description: "Boat transfer to island",
                        cost: 100,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: nil,
                    yachtPrices: [
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-5",
                            cabinType: "Standard",
                            cabinCount: 10,
                            divingPrice: 1400,
                            nonDivingPrice: 950
                        ),
                        Trip.PriceDetails.YachtPrice(
                            id: "cabin-6",
                            cabinType: "Master",
                            cabinCount: 2,
                            divingPrice: 1800,
                            nonDivingPrice: 1200
                        )
                    ],
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -1, to: today) ?? today,
                updatedAt: today
            ),
            // Trip 10 - Past (Archived)
            Trip(
                id: "trip-10",
                organizerId: testDiveCenterId,
                organizerType: Trip.OrganizerType.diveCenter,
                tripType: Trip.TripType.daily,
                hotelId: "hotel-6",
                yachtId: nil as String?,
                country: "Greece",
                region: "Crete",
                startDate: calendar.date(byAdding: .day, value: -120, to: today) ?? today,
                endDate: calendar.date(byAdding: .day, value: -113, to: today) ?? today,
                minimumCertificationLevel: "Open Water",
                minimumDives: 5,
                description: "Mediterranean diving in Crete. Explore caves, reefs, and historic underwater sites.",
                photos: [],
                totalSpots: 8,
                bookedSpots: 8,
                participants: [],
                availableCourses: ["course-1", "course-8"],
                nitroxAvailable: false,
                groupLeaderId: testInstructorId,
                program: [],
                additionalExpenses: [
                    Trip.AdditionalExpense(
                        id: "expense-14",
                        expenseType: .flight,
                        description: "Round trip flight",
                        cost: 500,
                        currency: "USD"
                    )
                ],
                equipmentRentalAvailable: true,
                priceDetails: Trip.PriceDetails(
                    roomPrices: [
                        Trip.PriceDetails.RoomPrice(
                            id: "room-9",
                            roomType: "Double",
                            roomCount: 6,
                            divingPrice: 700,
                            nonDivingPrice: 450
                        )
                    ],
                    yachtPrices: nil,
                    divingPrice: nil,
                    nonDivingPrice: nil,
                    currency: "USD"
                ),
                createdAt: calendar.date(byAdding: .month, value: -10, to: today) ?? today,
                updatedAt: calendar.date(byAdding: .day, value: -113, to: today) ?? today
            )
        ]
    }
    
    private static let calendar = Calendar.current
}
#endif
