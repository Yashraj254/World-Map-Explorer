package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RouteResponseDto(
    @field:Json(name = "trip") val trip: TripDto
) {
    @JsonClass(generateAdapter = true)
    data class TripDto(
        @field:Json(name = "legs") val legs: List<LegDto>,
        @field:Json(name = "summary") val summary: SummaryDto
    ) {
        @JsonClass(generateAdapter = true)
        data class LegDto(
            @field:Json(name = "shape") val shape: String,
        )

        @JsonClass(generateAdapter = true)
        data class SummaryDto(
            @field:Json(name = "length") val length: Double,
            @field:Json(name = "time") val time: Double
        )

    }
}
