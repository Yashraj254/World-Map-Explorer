package com.example.worldmapexplorer.data.models

data class RiverDetails(
    val name: String?,
    val length: String?,
    val origin: String?,
    val mouth: String?,
    val tributaries: String?,
    val coordinates: String?
): PlaceDetails() {
    data class Builder(
        var name: String? = null,
        var length: String? = null,
        var origin: String? = null,
        var mouth: String? = null,
        var tributaries: String? = null,
        var coordinates: String? = null
    ) {
        fun name(name: String) = apply { this.name = name }
        fun length(length: String) = apply { this.length = length }
        fun origin(origin: String) = apply { this.origin = origin }
        fun mouth(mouth: String) = apply { this.mouth = mouth }
        fun tributaries(tributaries: String) = apply { this.tributaries = tributaries }
        fun coordinates(coordinates: String) = apply { this.coordinates = coordinates }

        fun build(): RiverDetails {
            return RiverDetails(name, length, origin, mouth, tributaries, coordinates)
        }
    }
}


