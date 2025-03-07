package com.example.worldmapexplorer.utils

import org.osmdroid.util.GeoPoint
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.sin

// Decodes a polyline that was encoded by Valhalla into a list of GeoPoints
fun decodePolyline(encoded: String): List<GeoPoint> {
    val polyline = mutableListOf<GeoPoint>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1F shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1F shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        polyline.add(GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
    }
    return polyline
}

// Calculates the area of a polygon defined by a list of coordinates
fun calculatePolygonArea(coords: List<Pair<Double, Double>>): Double {
    val EARTH_RADIUS = 6371000.0 // Earth radius in meters

    var area = 0.0
    if (coords.size < 3) return area // A polygon must have at least 3 points

    for (i in coords.indices) {
        val (lat1, lon1) = coords[i]
        val (lat2, lon2) = coords[(i + 1) % coords.size] // Wrap around to first point

        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lonDiffRad = Math.toRadians(lon2 - lon1)

        area += lonDiffRad * (2 + sin(lat1Rad) + sin(lat2Rad))
    }
    area = abs(area * EARTH_RADIUS * EARTH_RADIUS / 2.0) / 1_000_000 // Convert m² to km²
    val roundedValue = Math.round(area * 1000) / 1000.0

    return DecimalFormat("#.000").format(roundedValue).toDouble() // Area in square meters
}

