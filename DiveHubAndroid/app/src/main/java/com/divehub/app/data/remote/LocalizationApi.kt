package com.divehub.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/** iOS `NetworkService.getTranslations` — table → key → value for one language. */
interface LocalizationApi {
    @GET("localization/{language}")
    suspend fun getTranslations(
        @Path("language") language: String,
    ): Map<String, Map<String, String>>
}
