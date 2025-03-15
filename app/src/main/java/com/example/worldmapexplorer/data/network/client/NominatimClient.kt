package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.GeocodingApi
import com.example.worldmapexplorer.data.network.api.GeometryApi
import com.example.worldmapexplorer.data.network.api.NominatimApi
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.skydoves.sandwich.ApiResponse
import javax.inject.Inject

class NominatimClient @Inject constructor(
    private val nominatimApi: NominatimApi,
    private val geometryApi: GeometryApi,
    private val geocodingApi: GeocodingApi
) {

    suspend fun fetchPlaces(query: String, excludedPaces: String) =
        nominatimApi.searchPlaces(query, excludedPaces)

    suspend fun getWayGeometry(query: String) = geometryApi.getWayGeometry(query)

    suspend fun getRelationGeometry(query: String) = geometryApi.getRelationGeometry(query)

    suspend fun getNodeGeometry(query: String) = geometryApi.getNodeGeometry(query)

    suspend fun getPlaceBorders(
        lat: Double,
        lon: Double,
        zoom: Int,
        polygonThreshold: Double
    ): ApiResponse<PlaceBordersDto> {
        return geocodingApi.getPlaceBorders(
            lat, lon, zoom, "geojson", 1, polygonThreshold
        )
    }

}