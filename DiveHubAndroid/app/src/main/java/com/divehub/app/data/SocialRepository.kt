package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.FriendRequestDto
import com.divehub.app.data.remote.dto.SendFriendRequestBody
import com.divehub.app.data.remote.dto.CreateGroupTripBody
import com.divehub.app.data.remote.dto.DiscoverNearbyDto
import com.divehub.app.data.remote.dto.FriendLocationDto
import com.divehub.app.data.remote.dto.GroupTripDto
import com.divehub.app.data.remote.dto.ReportLocationBody
import com.divehub.app.data.remote.dto.UserDto

class SocialRepository(private val graph: AppGraph) {
    suspend fun searchUsers(query: String): List<UserDto> = graph.socialApi().searchUsers(query)

    suspend fun friends(): List<UserDto> = graph.socialApi().friends()

    suspend fun receivedRequests(): List<FriendRequestDto> = graph.socialApi().receivedRequests()

    suspend fun sentRequests(): List<FriendRequestDto> = graph.socialApi().sentRequests()

    suspend fun sendRequest(userId: String) {
        graph.socialApi().sendRequest(SendFriendRequestBody(userId = userId))
    }

    suspend fun acceptRequest(userId: String) {
        graph.socialApi().acceptRequest(userId)
    }

    suspend fun declineRequest(friendshipId: String) {
        graph.socialApi().declineRequest(friendshipId)
    }

    suspend fun friendLocations(lat: Double?, lng: Double?): List<FriendLocationDto> =
        graph.socialApi().friendLocations(lat, lng)

    suspend fun discoverNearby(lat: Double, lng: Double): List<DiscoverNearbyDto> =
        graph.locationApi().discoverNearby(lat, lng)

    suspend fun reportLocation(lat: Double, lng: Double, accuracy: Double?) {
        graph.locationApi().reportLocation(
            ReportLocationBody(
                latitude = lat,
                longitude = lng,
                accuracyMeters = accuracy,
                source = "last_known",
            ),
        )
    }

    suspend fun groupTrips(): List<GroupTripDto> = graph.groupTripsApi().list()

    suspend fun createGroupTrip(body: CreateGroupTripBody): GroupTripDto =
        graph.groupTripsApi().create(body)
}
