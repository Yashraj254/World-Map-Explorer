package com.example.worldmapexplorer.utils

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.repository.GeoJsonGeometry
import de.jonaswolf.osmtogeojson.OsmToGeoJson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Decodes a polyline that was encoded by Valhalla into a list of GeoPoints
fun decodePolyline(encoded: String): List<Coordinates> {
    val polyline = mutableListOf<Coordinates>()
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

        polyline.add(Coordinates(lat.toDouble() / 1E6, lng.toDouble() / 1E6))
    }
    return polyline
}

// Calculates the area of a polygon defined by a list of coordinates

const val EARTH_RADIUS = 6371008.8
const val FACTOR = (EARTH_RADIUS * EARTH_RADIUS) / 2
const val PI_OVER_180 = Math.PI / 180.0


 fun polygonArea(coordinates: List<List<Coordinates>>): Double {
    var total = 0.0
    if (coordinates.isNotEmpty()) {
        total += Math.abs(ringArea(coordinates[0]))
        for (i in 1 until coordinates.size) {
            total -= Math.abs(ringArea(coordinates[i]))
        }
    }
    return total
}

/**
 * Calculate the approximate area of the polygon were it projected onto the earth.
 * Note that this area will be positive if ring is oriented clockwise, otherwise
 * it will be negative.
 *
 * Reference:
 * Robert. G. Chamberlain and William H. Duquette, "Some Algorithms for Polygons on a Sphere",
 * JPL Publication 07-03, Jet Propulsion
 * Laboratory, Pasadena, CA, June 2007 https://trs.jpl.nasa.gov/handle/2014/41271
 *
 * @param coordinates  A list of [Point] of Ring Coordinates
 * @return The approximate signed geodesic area of the polygon in square meters.
 */
private fun ringArea(coordinates: List<Coordinates>): Double {
    Log.d("Coordinates", "ringArea: $coordinates")
    lateinit var p1: Coordinates
    lateinit var p2: Coordinates
    lateinit var p3: Coordinates
    var lowerIndex: Int
    var middleIndex: Int
    var upperIndex: Int
    var total = 0.0
    val coordsLength = coordinates.size
    if (coordsLength > 2) {
        for (i in 0 until coordsLength) {
            if (i == coordsLength - 2) { // i = N-2
                lowerIndex = coordsLength - 2
                middleIndex = coordsLength - 1
                upperIndex = 0
            } else if (i == coordsLength - 1) { // i = N-1
                lowerIndex = coordsLength - 1
                middleIndex = 0
                upperIndex = 1
            } else { // i = 0 to N-3
                lowerIndex = i
                middleIndex = i + 1
                upperIndex = i + 2
            }
            p1 = coordinates[lowerIndex]
            p2 = coordinates[middleIndex]
            p3 = coordinates[upperIndex]
            total += (rad(p3.longitude) - rad(p1.longitude)) * Math.sin(rad(p2.latitude))
        }
        total = abs(total * EARTH_RADIUS * EARTH_RADIUS / 2.0) / 1_000_000 // Convert m² to km²
        val roundedValue = Math.round(total * 1000) / 1000.0
        total = roundedValue
    }


    return DecimalFormat("#.000").format(total).toDouble() }

private fun rad(num: Double): Double {
    return num * Math.PI / 180
}

/**
 * Data class to represent geographic coordinates (latitude, longitude)
 */
data class Coordinates(val latitude: Double, val longitude: Double)

fun Coordinates.toGeoPoint() =  GeoPoint(latitude, longitude)
/**
 * Data class to represent border points in all four directions
 */
data class BorderPoints(
    val north: Coordinates?,
    val south: Coordinates?,
    val east: Coordinates?,
    val west: Coordinates?
)

/**
 * Find all four border points (N, S, E, W) for a given location within multiple polygons
 * @param currentLocation The current location
 * @param polygons List of lists of GeoPoints, where each inner list represents a polygon
 * @return BorderPoints object containing the nearest border points in each direction
 */
fun findBorderPoints(currentLocation: Coordinates, polygons: List<List<Coordinates>>): BorderPoints {
    // Initialize result variables
    var northPoint: Coordinates? = null
    var southPoint: Coordinates? = null
    var eastPoint: Coordinates? = null
    var westPoint: Coordinates? = null

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
                (p1.longitude > currentLng && p2.longitude > currentLng)
            ) {
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
                (p1.latitude > currentLat && p2.latitude > currentLat)
            ) {
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
            northPoint = Coordinates(nearestNorth, currentLng)
        }
    }

    if (southValues.isNotEmpty()) {
        val nearestSouth = southValues.maxOrNull() ?: Double.NaN
        if (nearestSouth.isFinite()) {
            southPoint = Coordinates(nearestSouth, currentLng)
        }
    }

    if (eastValues.isNotEmpty()) {
        val nearestEast = eastValues.minOrNull() ?: Double.NaN
        if (nearestEast.isFinite()) {
            eastPoint = Coordinates(currentLat, nearestEast)
        }
    }

    if (westValues.isNotEmpty()) {
        val nearestWest = westValues.maxOrNull() ?: Double.NaN
        if (nearestWest.isFinite()) {
            westPoint = Coordinates(currentLat, nearestWest)
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
fun calculateDistance(point1: Coordinates, point2: Coordinates): Float {
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
fun BorderPoints.calculateDistances(currentLocation: Coordinates): Map<String, Float> {
    val distances = mutableMapOf<String, Float>()

    north?.let { distances["north"] = calculateDistance(currentLocation, it) }
    south?.let { distances["south"] = calculateDistance(currentLocation, it) }
    east?.let { distances["east"] = calculateDistance(currentLocation, it) }
    west?.let { distances["west"] = calculateDistance(currentLocation, it) }

    return distances
}

fun isPointInPolygon(point: Coordinates, polygonPoints: List<Coordinates>): Boolean {
    var intersections = 0
    val size = polygonPoints.size

    for (i in 0 until size) {
        val p1 = polygonPoints[i]
        val p2 = polygonPoints[(i + 1) % size]

        if ((p1.longitude > point.longitude) != (p2.longitude > point.longitude)) {
            val latIntersection =
                p1.latitude + (point.longitude - p1.longitude) * (p2.latitude - p1.latitude) / (p2.longitude - p1.longitude)
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

fun convertToGeoJSON(osmJson: String): GeoJsonGeometry {
    try {
        val geoJson = JSONObject(OsmToGeoJson().convertOverpassJsonToGeoJson(osmJson, null))
        // Print or use the GeoJSON
        val json = geoJson.getJSONArray("features").getJSONObject(0).getJSONObject("geometry")
        return parseGeoJsonGeometry(json)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return GeoJsonGeometry.Unknown("Something went wrong")

}

fun parseGeoJsonGeometry(json: JSONObject): GeoJsonGeometry {
    return try {
        val type = json.getString("type")
        val coordinates = json.getJSONArray("coordinates")

        when (type) {
            "Point" -> {
                val coord = coordinatesToPair(coordinates)
                GeoJsonGeometry.Point(coord)
            }

            "MultiPoint", "LineString" -> {
                val coordList = coordinatesToList(coordinates)
                if (type == "MultiPoint") GeoJsonGeometry.MultiPoint(coordList)
                else GeoJsonGeometry.LineString(coordList)
            }

            "Polygon", "MultiLineString" -> {
                val coordList = coordinatesToListOfLists(coordinates)
                if (type == "Polygon") GeoJsonGeometry.Polygon(coordList)
                else GeoJsonGeometry.MultiLineString(coordList)
            }

            "MultiPolygon" -> {
                val coordList = coordinatesToListOfListsOfLists(coordinates)
                GeoJsonGeometry.MultiPolygon(coordList)
            }

            else -> GeoJsonGeometry.Unknown("Something went wrong")
        }
    } catch (e: JSONException) {
        e.printStackTrace()
        GeoJsonGeometry.Unknown("Something went wrong")
    }
}

fun coordinatesToPair(array: JSONArray): Coordinates {
    return Coordinates(array.getDouble(1), array.getDouble(0))
}

fun coordinatesToList(array: JSONArray): List<Coordinates> {
    return List(array.length()) { i ->
        coordinatesToPair(array.getJSONArray(i))
    }
}

fun coordinatesToListOfLists(array: JSONArray): List<List<Coordinates>> {
    return List(array.length()) { i ->
        coordinatesToList(array.getJSONArray(i))
    }
}

fun coordinatesToListOfListsOfLists(array: JSONArray): List<List<List<Coordinates>>> {
    return List(array.length()) { i ->
        coordinatesToListOfLists(array.getJSONArray(i))
    }
}


// Usage
//val distances = getDistanceToBorders(userLocation, selectedBoundary)
//Log.d("BorderDistances", "N: ${distances["North"]}, S: ${distances["South"]}, E: ${distances["East"]}, W: ${distances["West"]}")

