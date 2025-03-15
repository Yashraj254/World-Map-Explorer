package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RelationGeometry(
    @field: Json(name = "elements") val elements: List<Element>
) {
    @JsonClass(generateAdapter = true)
    data class Element(
        @field: Json(name = "members") val members: List<Member>,
        @field: Json(name = "bounds") val bounds: Bounds,
        @field: Json(name = "tags") val tags: Map<String, String>
    ) {
        @JsonClass(generateAdapter = true)
        data class Member(
            @field: Json(name = "geometry") val geometry: List<LatLon>,
        )

        @JsonClass(generateAdapter = true)
        data class Bounds(
            @field: Json(name = "minlat") val minLat: Double,
            @field: Json(name = "minlon") val minLon: Double,
            @field: Json(name = "maxlat") val maxLat: Double,
            @field: Json(name = "maxlon") val maxLon: Double
        )
    }
}
