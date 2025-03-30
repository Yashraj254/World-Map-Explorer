package com.example.worldmapexplorer.data.models

data class OtherAreaDetails(
    val name: String,
    val type: String,
    val coordinates: String,
    val area: String,
    val address: String
): PlaceDetails() {

    class Builder {
        private var name: String = ""
        private var type: String = ""
        private var coordinates: String = ""
        private var area: String = ""
        private var address: String = ""

        fun name(name: String) = apply { this.name = name }
        fun type(type: String) = apply { this.type = type }
        fun coordinates(coordinates: String) = apply { this.coordinates = coordinates }
        fun area(area: String) = apply { this.area = area }
        fun address(address: String) = apply { this.address = address }

        fun build() = OtherAreaDetails(name, type, coordinates, area, address)
    }
}