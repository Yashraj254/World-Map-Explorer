package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.GeometryApi
import com.example.worldmapexplorer.data.network.api.NominatimApi
import javax.inject.Inject

class NominatimClient @Inject constructor(
    private val nominatimApi: NominatimApi,
    private val geometryApi: GeometryApi) {

    suspend fun fetchPlaces(query: String, excludedPaces: String) =
        nominatimApi.searchPlaces(query, excludedPaces)

    suspend fun getWayGeometry(query: String) = geometryApi.getWayGeometry(query)

}