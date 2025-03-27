package com.example.worldmapexplorer.data.models

data class DistrictDetails(
    val name: String,
    val state: String,
    val area: String,
    val coordinates: String,
    val borders: String,
    val summary: String
): PlaceDetails() {
    class Builder {
        private var name: String = ""
        private var state: String = ""
        private var area: String = ""
        private var coordinates: String = ""
        private var borders: String = ""
        private var summary: String = ""

        fun name(name: String) = apply { this.name = name }
        fun state(state: String) = apply { this.state = state }
        fun area(area: String) = apply { this.area = area }
        fun coordinates(coordinates: String) = apply { this.coordinates = coordinates }
        fun borders(borders: String) = apply { this.borders = borders }
        fun summary(summary: String) = apply { this.summary = summary }

        fun build() = DistrictDetails(name, state, area, coordinates, borders, summary)
    }
}
