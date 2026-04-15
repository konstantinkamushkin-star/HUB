package com.divehub.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ErrorStatsTotalsDto(
    @SerializedName("httpErrors") val httpErrors: Int? = null,
    @SerializedName("uncaughtExceptions") val uncaughtExceptions: Int? = null,
    @SerializedName("unhandledRejections") val unhandledRejections: Int? = null,
    @SerializedName("allErrors") val allErrors: Int? = null,
)

data class ErrorStatsDto(
    @SerializedName("totals") val totals: ErrorStatsTotalsDto? = null,
)

data class AdminOverviewCountsDto(
    @SerializedName("users") val users: Int? = null,
    @SerializedName("diveCenters") val diveCenters: Int? = null,
    @SerializedName("diveSites") val diveSites: Int? = null,
    @SerializedName("diveLogs") val diveLogs: Int? = null,
    @SerializedName("feedPosts") val feedPosts: Int? = null,
)

data class AdminOverviewDto(
    @SerializedName("generatedAt") val generatedAt: String? = null,
    @SerializedName("counts") val counts: AdminOverviewCountsDto? = null,
    @SerializedName("systemHealth") val systemHealth: ErrorStatsDto? = null,
)
