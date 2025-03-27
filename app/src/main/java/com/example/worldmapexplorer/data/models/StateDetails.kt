package com.example.worldmapexplorer.data.models

data class StateDetails(
    val name: String,
    val country: String?,
    val capital: String?,
    val area: String?,
    val coordinates: String?,
    val borders: String?,
    val summary: String?
): PlaceDetails() {

    class Builder {
        private var name: String = ""
        private var country: String? = null
        private var capital: String? = null
        private var area: String? = null
        private var coordinates: String? = null
        private var borders: String? = null
        private var summary: String? = null

        fun name(name: String) = apply { this.name = name }
        fun country(country: String?) = apply { this.country = country }
        fun capital(capital: String?) = apply { this.capital = capital }
        fun area(area: String?) = apply { this.area = area }
        fun coordinates(coordinates: String?) = apply { this.coordinates = coordinates }
        fun borders(borders: String?) = apply { this.borders = borders }
        fun summary(summary: String?) = apply { this.summary = summary }

        fun build() = StateDetails(name, country, capital, area, coordinates, borders, summary)
    }
}