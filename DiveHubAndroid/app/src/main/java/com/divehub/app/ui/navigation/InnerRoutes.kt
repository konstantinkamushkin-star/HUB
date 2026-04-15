package com.divehub.app.ui.navigation

object InnerRoutes {
    const val Home = "home"
    const val EditProfile = "edit_profile"
    const val Search = "search"
    const val Trips = "trips"
    const val TripCreate = "trip_create"
    const val TripEdit = "trip_edit/{tripId}"

    fun tripEdit(tripId: String) = "trip_edit/$tripId"

    const val TripDetail = "trip_detail/{tripId}"

    fun tripDetail(tripId: String) = "trip_detail/$tripId"

    const val CenterInstructors = "center_instructors/{centerId}"

    fun centerInstructors(centerId: String) = "center_instructors/$centerId"

    const val CenterTrips = "center_trips/{centerId}"

    fun centerTrips(centerId: String) = "center_trips/$centerId"

    /** Diver-facing public dive center profile (iOS `DiveCenterPublicView`). */
    const val DiveCenterPublic = "dive_center_public/{centerId}"

    fun diveCenterPublic(centerId: String) = "dive_center_public/$centerId"

    /** Public shop profile (`GET v1/shops/:id`). */
    const val ShopPublic = "shop_public/{shopId}"

    fun shopPublic(shopId: String) = "shop_public/$shopId"

    const val UserProfile = "user_profile/{userId}"
    fun userProfile(userId: String) = "user_profile/$userId"

    const val Help = "help"
    const val Notifications = "notifications"
    const val Settings = "settings"
    const val Statistics = "statistics"
    const val Achievements = "achievements"
    const val AdminGearManagement = "admin_gear_management"
    const val AdminShopsManagement = "admin_shops_management"
    const val AdminBookingManagement = "admin_booking_management"
    const val AdminBookingCalendar = "admin_booking_calendar"
    const val AdminAffiliatedSites = "admin_affiliated_sites"
    const val Inventory = "inventory"

    const val Subscription = "subscription"
    const val Certifications = "certifications"
    const val GearProfiles = "gear_profiles"
    const val PrivacySettings = "privacy_settings"
    const val NotificationSettings = "notification_settings"
    const val MeasurementUnits = "measurement_units"

    /** Full-screen underwater / dive editor (parity with iOS Photo processing tab). */
    const val DiveEditor = "dive_editor"

    /** Full-screen OSM map (iOS `MapTabView`-style; opened from Explore). */
    const val MapFullscreen = "map_fullscreen"

    /**
     * Multi-step booking wizard (iOS `BookingWizardView` + optional course like `CourseBookingView`).
     * Use "-" for unused path segments (NavHost path args).
     */
    const val BookingWizard = "booking_wizard/{centerId}/{siteId}/{instructorId}/{courseId}"

    fun bookingWizard(
        centerId: String? = null,
        siteId: String? = null,
        instructorId: String? = null,
        courseId: String? = null,
    ): String {
        fun seg(s: String?) = if (s.isNullOrBlank()) "-" else s
        return "booking_wizard/${seg(centerId)}/${seg(siteId)}/${seg(instructorId)}/${seg(courseId)}"
    }

    /** iOS `BusinessChatLaunchView` — opens peer chat then returns to diver chat tab. */
    const val BusinessChatOpen = "business_chat/{peerType}/{peerId}"

    fun businessChatOpen(peerType: String, peerId: String) = "business_chat/$peerType/$peerId"
}
