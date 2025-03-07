package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LatLon(
    @field: Json(name = "lat") val lat: Double,
    @field: Json(name = "lon") val lon: Double
)