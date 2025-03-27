package com.example.worldmapexplorer.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaceDetailsDto(
    @field: Json(name = "extratags") val tags: Wikidata,
    @field: Json(name = "geometry") val geometry: Coordinates
) {
    @JsonClass(generateAdapter = true)
    data class Wikidata(
        @field: Json(name = "wikidata") val wikidata: String
    )

    @JsonClass(generateAdapter = true)
    data class Coordinates(
        @field: Json(name = "coordinates") val coordinates: List<Double>
    )
}
