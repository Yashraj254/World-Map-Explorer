package com.example.worldmapexplorer.data.repository

import com.example.worldmapexplorer.utils.Coordinates

sealed class GeoJsonGeometry {
    abstract val type: String

    data class Point(val coordinates: Coordinates) : GeoJsonGeometry() {
        override val type = "Point"
    }

    data class MultiPoint(val coordinates: List<Coordinates>) : GeoJsonGeometry() {
        override val type = "MultiPoint"
    }

    data class LineString(val coordinates: List<Coordinates>) : GeoJsonGeometry() {
        override val type = "LineString"
    }

    data class MultiLineString(val coordinates: List<List<Coordinates>>) : GeoJsonGeometry() {
        override val type = "MultiLineString"
    }

    data class Polygon(val coordinates: List<List<Coordinates>>) : GeoJsonGeometry() {
        override val type = "Polygon"
    }

    data class MultiPolygon(val coordinates: List<List<List<Coordinates>>>) : GeoJsonGeometry() {
        override val type = "MultiPolygon"
    }

    data class Unknown(val error: String) : GeoJsonGeometry() {
        override val type = "Unknown"
    }
}
