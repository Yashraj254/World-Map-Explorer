package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Bounds(
    @field: Json(name = "minlat") val minLat: Double,
    @field: Json(name = "minlon") val minLon: Double,
    @field: Json(name = "maxlat") val maxLat: Double,
    @field: Json(name = "maxlon") val maxLon: Double
)