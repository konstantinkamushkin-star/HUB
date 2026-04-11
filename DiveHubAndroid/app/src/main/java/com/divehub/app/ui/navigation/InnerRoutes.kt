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

    const val UserProfile = "user_profile/{userId}"
    fun userProfile(userId: String) = "user_profile/$userId"

    const val Help = "help"
    const val Notifications = "notifications"
    const val Settings = "settings"
    const val Statistics = "statistics"
    const val Achievements = "achievements"

    const val Subscription = "subscription"
    const val Certifications = "certifications"
    const val GearProfiles = "gear_profiles"
    const val PrivacySettings = "privacy_settings"
    const val NotificationSettings = "notification_settings"
    const val MeasurementUnits = "measurement_units"
}
