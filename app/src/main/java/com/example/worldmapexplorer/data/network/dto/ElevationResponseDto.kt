package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ElevationResponseDto(
    @field: Json(name = "results") val results: List<Result>
) {
    @JsonClass(generateAdapter = true)
    data class Result(
        @field: Json(name = "latitude") val latitude: Double,
        @field: Json(name = "longitude") val longitude: Double,
        @field: Json(name = "elevation") val elevation: Double
    )
}
