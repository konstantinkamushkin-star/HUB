package com.divehub.app.data.repository

import android.content.Context
import android.net.Uri
import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.CreateTripRequestDto
import com.divehub.app.data.remote.dto.ImportTripUrlRequestDto
import com.divehub.app.data.remote.dto.ImportTripUrlResponseDto
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.divehub.app.data.remote.dto.CourseRemoteDto
import com.divehub.app.data.remote.dto.CourseWriteRequestDto
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.TripCreatedResponseDto
import com.divehub.app.data.remote.dto.TripJoinResponseDto
import com.divehub.app.data.remote.dto.TripListItemDto
import com.divehub.app.data.remote.dto.UpdateTripRequestDto
import com.divehub.app.data.remote.dto.UserDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class TripsRepository(private val graph: AppGraph) {
    suspend fun listTrips(organizerId: String? = null): List<TripListItemDto> =
        graph.tripsApi().listTrips(organizerId = organizerId)

    suspend fun getTrip(id: String): TripListItemDto = graph.tripsApi().getTrip(id)

    suspend fun joinTrip(id: String): TripJoinResponseDto = graph.tripsApi().joinTrip(id)

    suspend fun listManagedDiveCenters(): List<DiveCenterBriefDto> =
        graph.partnerAdminApi().listManagedCenters()

    suspend fun listCoursesForCenter(diveCenterId: String): List<CourseListItemDto> =
        graph.coursesApi().listCourses(diveCenterId = diveCenterId)

    suspend fun createCourse(body: CourseWriteRequestDto): CourseRemoteDto =
        graph.coursesApi().createCourse(body)

    suspend fun updateCourse(id: String, body: CourseWriteRequestDto): CourseRemoteDto =
        graph.coursesApi().patchCourse(id, body)

    suspend fun deleteCourse(id: String) {
        graph.coursesApi().deleteCourse(id)
    }

    suspend fun listInstructorsForCenter(centerId: String): List<UserDto> =
        graph.partnerAdminApi().listCenterInstructors(centerId)

    suspend fun createTrip(body: CreateTripRequestDto): TripCreatedResponseDto =
        graph.tripsApi().createTrip(body)

    suspend fun updateTrip(id: String, body: UpdateTripRequestDto): TripCreatedResponseDto =
        graph.tripsApi().updateTrip(id, body)

    suspend fun deleteTrip(id: String) {
        graph.tripsApi().deleteTrip(id)
    }

    suspend fun importTripFromUrl(url: String, diveCenterId: String): ImportTripUrlResponseDto =
        graph.tripsApi().importTripFromUrl(ImportTripUrlRequestDto(url = url, diveCenterId = diveCenterId))

    /** Same endpoint as feed / logbook: `POST media/upload` → `/api/media/files/{uuid}.jpg`. */
    suspend fun uploadTripPhoto(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("Unable to read image")
        val temp = File.createTempFile("trip_photo_", ".jpg", context.cacheDir)
        temp.outputStream().use { out -> input.use { it.copyTo(out) } }
        val body = temp.asRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("file", temp.name, body)
        val res = graph.feedApi().uploadMedia(part)
        temp.delete()
        return res.path ?: res.url ?: error("Upload failed")
    }
}
