package com.example.worldmapexplorer.data.network.dto

class PlaceInfo(
    val name: String,
    val address: String,
    val type: String,
    val area: Float? = null
) {
    class Builder {
        private var name: String = ""
        private var address: String = ""
        private var type: String = ""
        private var area: Float = 0f

        fun setName(name: String) = apply { this.name = name }
        fun setType(type: String) = apply { this.type = type }
        fun setAddress(address: String) = apply { this.address = address }
        fun setArea(area: Float) = apply { this.area = area }

        fun build(): PlaceInfo {
            return PlaceInfo(name, address, type, area)
        }
    }
}

