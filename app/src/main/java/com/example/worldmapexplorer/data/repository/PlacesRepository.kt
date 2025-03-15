package com.example.worldmapexplorer.data.repository

import android.content.Context
import com.example.worldmapexplorer.data.network.client.ElevationClient
import com.example.worldmapexplorer.data.network.client.NominatimClient
import com.example.worldmapexplorer.data.network.client.RouteClient
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.example.worldmapexplorer.data.network.dto.RouteRequestDto
import com.example.worldmapexplorer.data.network.dto.RouteResponseDto
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.pow

class PlacesRepository @Inject constructor(
    private val nominatimClient: NominatimClient,
    private val routeClient: RouteClient,
    private val elevationClient: ElevationClient,
    @ApplicationContext private val context: Context
) {

    suspend fun fetchPlaces(query: String, excludedPaces: String) =
        nominatimClient.fetchPlaces(query, excludedPaces)

    suspend fun getWayGeometry(query: String) = nominatimClient.getWayGeometry(query)

    suspend fun getRelationGeometry(query: String) = nominatimClient.getRelationGeometry(query)

    suspend fun getNodeGeometry(query: String) = nominatimClient.getNodeGeometry(query)

    suspend fun fetchPrefix(result: Map<String, String>): String {
        val jsonString = context.assets.open("prefix.json").bufferedReader().use { it.readText() }
        val data = JSONObject(jsonString)
        val prefixes = data.getJSONObject("prefix")

        var prefix = ""

        if (result["boundary"] == "administrative" && result.containsKey("admin_level")) {
            val adminLevel = "level" + result["admin_level"]
            prefix = prefixes.getJSONObject("admin_levels").optString(adminLevel, "")
        } else {
            for ((key, value) in result) {
                if (prefixes.has(key)) {
                    val keyObject = prefixes.getJSONObject(key)
                    if (keyObject.has(value)) {
                        return keyObject.getString(value)
                    }
                }
            }

            for ((key, value) in result) {
                if (prefixes.has(key)) {
                    val formattedValue =
                        value.replaceFirstChar { it.uppercaseChar() }.replace("_", " ")
                    return formattedValue
                }
            }
        }

        return prefix
    }

    suspend fun getRoute(locations: List<LatLon>): ApiResponse<RouteResponseDto> {
        val routeRequestDto =
            RouteRequestDto(locations, "auto", RouteRequestDto.DirectionsOptions("km"))
        return routeClient.getRoute(routeRequestDto)
    }

    suspend fun getElevation(lat: Double, lon: Double) = elevationClient.getElevation(lat, lon)

    suspend fun getPlacesBorder(
        lat: Double,
        lon: Double,
        zoom: Int,
    ): ApiResponse<PlaceBordersDto> {
       return nominatimClient.getPlaceBorders(
            lat,
            lon,
            getFixedZoom(zoom),
            getThreshold(zoom.toDouble())
        )
    }

    private fun getFixedZoom(zoom: Int): Int {
        return if (zoom >= 8) {
            6
        } else if (zoom in 5..7) {
            5
        } else {
            2
        }
    }

    private fun getThreshold(zoom: Double): Double {
        return 1 / zoom.pow(3)
    }

}