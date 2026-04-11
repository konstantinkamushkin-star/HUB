package com.divehub.app.data.remote

import com.divehub.app.data.remote.dto.PartnerRegistrationResponseDto
import com.divehub.app.data.remote.dto.SubmitPartnerRegistrationRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface PartnerRegistrationApi {
    @POST("v1/partner-registrations")
    suspend fun submit(@Body body: SubmitPartnerRegistrationRequestDto): PartnerRegistrationResponseDto
}
