package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaceDetails(
    @field: Json(name = "elements") val elements: List<Element>
) {
    @JsonClass(generateAdapter = true)
    data class Element(
        @field: Json(name = "geometry") val geometry: List<LatLon>,
        @field: Json(name = "tags") val tags: Map<String,String>
    )
}
