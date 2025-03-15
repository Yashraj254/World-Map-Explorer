package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaceBordersDto(
    @field: Json(name = "features") val features: List<Feature>
) {
    @JsonClass(generateAdapter = true)
    data class Feature(
        @field: Json(name = "geometry") val geometry: Geometry,
    ) {
        @JsonClass(generateAdapter = true)
        data class Geometry(
            @field: Json(name = "type") val type: String,
            @field: Json(name = "coordinates") val coordinates: List<*> // 3 levels
        )
    }
}
