package com.example.worldmapexplorer.data.models

data  class CountryDetails(
    val name: String?,
    val capital: String?,
    val continent: String?,
    val language: String?,
    val population: String?,
    val borders: String?,
    val area: String?,
    val coordinates: String?,
    val northernmostPoint: String?,
    val southernmostPoint: String?,
    val easternmostPoint: String?,
    val westernmostPoint: String?
): PlaceDetails() {

    class Builder {
        private var name: String? = null
        private var capital: String? = null
        private var continent: String? = null
        private var language: String? = null
        private var population: String? = null
        private var borders: String? = null
        private var area: String? = null
        private var coordinates: String? = null
        private var northernmostPoint: String? = null
        private var southernmostPoint: String? = null
        private var easternmostPoint: String? = null
        private var westernmostPoint: String? = null

        fun name(name: String) = apply { this.name = name }
        fun capital(capital: String) = apply { this.capital = capital }
        fun continent(continent: String) = apply { this.continent = continent }
        fun language(language: String) = apply { this.language = language }
        fun population(population: String) = apply { this.population = population }
        fun borders(borders: String) = apply { this.borders = borders }
        fun area(area: String) = apply { this.area = area }
        fun coordinates(coordinates: String) = apply { this.coordinates = coordinates }
        fun northernmostPoint(northernmostPoint: String) = apply { this.northernmostPoint = northernmostPoint }
        fun southernmostPoint(southernmostPoint: String) = apply { this.southernmostPoint = southernmostPoint }
        fun easternmostPoint(easternmostPoint: String) = apply { this.easternmostPoint = easternmostPoint }
        fun westernmostPoint(westernmostPoint: String) = apply { this.westernmostPoint = westernmostPoint }

        fun build() = CountryDetails(
            name, capital, continent, language, population, borders, area, coordinates,
            northernmostPoint, southernmostPoint, easternmostPoint, westernmostPoint
        )
    }
}

