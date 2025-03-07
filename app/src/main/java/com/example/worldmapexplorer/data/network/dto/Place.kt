package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Place(
    @field: Json(name = "place_id") val placeId: Long,
    @field: Json(name = "osm_id") val osmId: Long,
    @field: Json(name = "display_name") val displayName: String,
    @field: Json(name = "name") val name: String,
    @field: Json(name = "type") val type: String
)