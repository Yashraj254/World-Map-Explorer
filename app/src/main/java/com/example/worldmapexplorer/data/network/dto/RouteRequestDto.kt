package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RouteRequestDto(
    @field: Json(name = "locations") val locations: List<LatLon>,
    @field: Json(name = "costing") val costing: String,
    @field: Json(name = "directions_options") val directionsOptions: DirectionsOptions
) {
    @JsonClass(generateAdapter = true)
    data class DirectionsOptions(
        @field: Json(name = "units") val units: String
    )
}