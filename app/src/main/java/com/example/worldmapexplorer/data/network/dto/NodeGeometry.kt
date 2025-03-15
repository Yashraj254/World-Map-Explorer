package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class NodeGeometry(
    @field: Json(name = "elements") val elements: List<Element>
) {
    @JsonClass(generateAdapter = true)
    data class Element(
        @field: Json(name = "lat") val latitude: Double,
        @field: Json(name = "lon") val longitude: Double,
        @field: Json(name = "tags") val tags: Map<String, String>
    )
}