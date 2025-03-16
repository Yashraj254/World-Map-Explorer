package com.example.worldmapexplorer.utils

import android.content.Context
import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import java.text.DecimalFormat
import kotlin.math.*

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

        polyline.add(GeoPoint(lat.toDouble() / 1E6, lng.toDouble() / 1E6))
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

/**
 * Data class to represent geographic coordinates (latitude, longitude)
 */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * Data class to represent border points in all four directions
 */
data class BorderPoints(
    val north: GeoPoint?,
    val south: GeoPoint?,
    val east: GeoPoint?,
    val west: GeoPoint?
)

/**
 * Find all four border points (N, S, E, W) for a given location within multiple polygons
 * @param currentLocation The current location
 * @param polygons List of lists of GeoPoints, where each inner list represents a polygon
 * @return BorderPoints object containing the nearest border points in each direction
 */
fun findBorderPoints(currentLocation: GeoPoint, polygons: List<List<GeoPoint>>): BorderPoints {
    // Initialize result variables
    var northPoint: GeoPoint? = null
    var southPoint: GeoPoint? = null
    var eastPoint: GeoPoint? = null
    var westPoint: GeoPoint? = null

    // Current location coordinates
    val currentLat = currentLocation.latitude
    val currentLng = currentLocation.longitude

    // Find north and south border points
    val northValues = mutableListOf<Double>()
    val southValues = mutableListOf<Double>()

    for (polygon in polygons) {
        // Process each polygon
        for (i in 0 until polygon.size) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]

            // Skip if both points are on same side of current longitude
            if ((p1.longitude < currentLng && p2.longitude < currentLng) ||
                (p1.longitude > currentLng && p2.longitude > currentLng)) {
                continue
            }

            // Skip if segment is parallel to meridian
            if (p1.longitude == p2.longitude) {
                // Handle vertical line at current longitude
                if (p1.longitude == currentLng) {
                    val minLat = min(p1.latitude, p2.latitude)
                    val maxLat = max(p1.latitude, p2.latitude)

                    if (minLat > currentLat) northValues.add(minLat)
                    if (maxLat < currentLat) southValues.add(maxLat)
                }
                continue
            }

            // Calculate intersection point
            val t = (currentLng - p1.longitude) / (p2.longitude - p1.longitude)
            if (t in 0.0..1.0) {
                val intersectionLat = p1.latitude + t * (p2.latitude - p1.latitude)

                // Check if point is north or south of current location
                if (intersectionLat > currentLat) {
                    northValues.add(intersectionLat)
                } else if (intersectionLat < currentLat) {
                    southValues.add(intersectionLat)
                }
            }
        }
    }

    // Find east and west border points
    val eastValues = mutableListOf<Double>()
    val westValues = mutableListOf<Double>()

    for (polygon in polygons) {
        for (i in 0 until polygon.size) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]

            // Skip if both points are on same side of current latitude
            if ((p1.latitude < currentLat && p2.latitude < currentLat) ||
                (p1.latitude > currentLat && p2.latitude > currentLat)) {
                continue
            }

            // Skip if segment is parallel to latitude
            if (p1.latitude == p2.latitude) {
                // Handle horizontal line at current latitude
                if (p1.latitude == currentLat) {
                    val minLng = min(p1.longitude, p2.longitude)
                    val maxLng = max(p1.longitude, p2.longitude)

                    if (minLng > currentLng) eastValues.add(minLng)
                    if (maxLng < currentLng) westValues.add(maxLng)
                }
                continue
            }

            // Calculate intersection point
            val t = (currentLat - p1.latitude) / (p2.latitude - p1.latitude)
            if (t in 0.0..1.0) {
                val intersectionLng = p1.longitude + t * (p2.longitude - p1.longitude)

                // Check if point is east or west of current location
                if (intersectionLng > currentLng) {
                    eastValues.add(intersectionLng)
                } else if (intersectionLng < currentLng) {
                    westValues.add(intersectionLng)
                }
            }
        }
    }

    // Find nearest border points
    if (northValues.isNotEmpty()) {
        val nearestNorth = northValues.minOrNull() ?: Double.NaN
        if (nearestNorth.isFinite()) {
            northPoint = GeoPoint(nearestNorth, currentLng)
        }
    }

    if (southValues.isNotEmpty()) {
        val nearestSouth = southValues.maxOrNull() ?: Double.NaN
        if (nearestSouth.isFinite()) {
            southPoint = GeoPoint(nearestSouth, currentLng)
        }
    }

    if (eastValues.isNotEmpty()) {
        val nearestEast = eastValues.minOrNull() ?: Double.NaN
        if (nearestEast.isFinite()) {
            eastPoint = GeoPoint(currentLat, nearestEast)
        }
    }

    if (westValues.isNotEmpty()) {
        val nearestWest = westValues.maxOrNull() ?: Double.NaN
        if (nearestWest.isFinite()) {
            westPoint = GeoPoint(currentLat, nearestWest)
        }
    }

    return BorderPoints(northPoint, southPoint, eastPoint, westPoint)
}

/**
 * Calculate distance between two geographic points in meters
 * @param point1 First point
 * @param point2 Second point
 * @return Distance in meters
 */
fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        point1.latitude, point1.longitude,
        point2.latitude, point2.longitude,
        results
    )
    return Math.round(results[0] / 1000 * 100) / 100f
}

/**
 * Extension function to calculate distance from current location to border points
 * @return Map of distances to each border in meters
 */
fun BorderPoints.calculateDistances(currentLocation: GeoPoint): Map<String, Float> {
    val distances = mutableMapOf<String, Float>()

    north?.let { distances["north"] = calculateDistance(currentLocation, it) }
    south?.let { distances["south"] = calculateDistance(currentLocation, it) }
    east?.let { distances["east"] = calculateDistance(currentLocation, it) }
    west?.let { distances["west"] = calculateDistance(currentLocation, it) }

    return distances
}

fun isPointInPolygon(point: GeoPoint, polygonPoints: List<GeoPoint>): Boolean {
    var intersections = 0
    val size = polygonPoints.size

    for (i in 0 until size) {
        val p1 = polygonPoints[i]
        val p2 = polygonPoints[(i + 1) % size]

        if ((p1.longitude > point.longitude) != (p2.longitude > point.longitude)) {
            val latIntersection = p1.latitude + (point.longitude - p1.longitude) * (p2.latitude - p1.latitude) / (p2.longitude - p1.longitude)
            if (point.latitude < latIntersection) {
                intersections++
            }
        }
    }
    return (intersections % 2 == 1) // Odd number of intersections -> inside
}


fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

fun convertSeconds(seconds: Double): String {
    val hours = (seconds / 3600).toInt()
    val minutes = ((seconds % 3600) / 60).toInt()
    return "$hours hours and $minutes minutes"
}
// Usage
//val distances = getDistanceToBorders(userLocation, selectedBoundary)
//Log.d("BorderDistances", "N: ${distances["North"]}, S: ${distances["South"]}, E: ${distances["East"]}, W: ${distances["West"]}")

