package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.AdminGearCreateRequestDto
import com.divehub.app.data.remote.dto.AdminGearPatchStatusDto
import com.divehub.app.data.remote.dto.AdminGearRemoteDto
import com.divehub.app.data.remote.dto.AffiliatedSitesResponseDto
import com.divehub.app.data.remote.dto.AffiliatedSitesWriteDto
import com.divehub.app.data.remote.dto.DiveCenterBriefDto
import com.divehub.app.data.remote.dto.InventoryItemLocal
import com.divehub.app.data.remote.dto.MaintenanceTicketLocal
import com.divehub.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PartnerAdminApi {
    @GET("admin/centers/managed")
    suspend fun listManagedCenters(): List<DiveCenterBriefDto>

    @GET("admin/centers/{centerId}/instructors")
    suspend fun listCenterInstructors(@Path("centerId") centerId: String): List<UserDto>

    @GET("admin/centers/{centerId}/affiliated-sites")
    suspend fun getAffiliatedSites(@Path("centerId") centerId: String): AffiliatedSitesResponseDto

    @PATCH("admin/centers/{centerId}/affiliated-sites")
    suspend fun patchAffiliatedSites(
        @Path("centerId") centerId: String,
        @Body body: AffiliatedSitesWriteDto,
    ): AffiliatedSitesResponseDto

    @GET("admin/centers/{centerId}/gear")
    suspend fun listCenterGear(@Path("centerId") centerId: String): List<AdminGearRemoteDto>

    @POST("admin/centers/{centerId}/gear")
    suspend fun createCenterGear(
        @Path("centerId") centerId: String,
        @Body body: AdminGearCreateRequestDto,
    ): AdminGearRemoteDto

    @PATCH("admin/gear/{gearId}/status")
    suspend fun patchGearStatus(
        @Path("gearId") gearId: String,
        @Body body: AdminGearPatchStatusDto,
    ): AdminGearRemoteDto

    @GET("admin/centers/{centerId}/inventory/items")
    suspend fun listInventoryItems(@Path("centerId") centerId: String): List<InventoryItemLocal>

    @POST("admin/centers/{centerId}/inventory/items")
    suspend fun upsertInventoryItem(
        @Path("centerId") centerId: String,
        @Body body: InventoryItemLocal,
    ): InventoryItemLocal

    @DELETE("admin/inventory/items/{itemId}")
    suspend fun deleteInventoryItem(@Path("itemId") itemId: String)

    @GET("admin/centers/{centerId}/inventory/tickets")
    suspend fun listInventoryTickets(@Path("centerId") centerId: String): List<MaintenanceTicketLocal>

    @POST("admin/centers/{centerId}/inventory/tickets")
    suspend fun upsertInventoryTicket(
        @Path("centerId") centerId: String,
        @Body body: MaintenanceTicketLocal,
    ): MaintenanceTicketLocal
}
