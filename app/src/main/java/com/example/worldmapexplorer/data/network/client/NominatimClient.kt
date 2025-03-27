package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.GeocodingApi
import com.example.worldmapexplorer.data.network.api.GeometryApi
import com.example.worldmapexplorer.data.network.api.NominatimApi
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.example.worldmapexplorer.utils.convertToGeoJSON
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.suspendOnSuccess
import okhttp3.ResponseBody
import javax.inject.Inject

class NominatimClient @Inject constructor(
    private val nominatimApi: NominatimApi,
    private val geometryApi: GeometryApi,
    private val geocodingApi: GeocodingApi
) {

    suspend fun fetchPlaces(query: String, excludedPaces: String) =
        nominatimApi.searchPlaces(query, excludedPaces)

    suspend fun getWayGeometry(query: String) = geometryApi.getWayGeometry(query)

    suspend fun getGeometry(query: String) = geometryApi.getGeometry(query)

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

    suspend fun getPlaceDetailsForWiki( osmId: Long, type: String): ApiResponse<ResponseBody>{
      return  nominatimApi.placeDetails(type[0]. uppercase(),osmId)
    }
    suspend fun getPlaceDetails(
        lat: Double,
        lon: Double,
        zoom: Int,
    ): ApiResponse<Place> {
        return geocodingApi.getPlaceDetails(
            lat, lon, zoom, "jsonv2"
        )
    }

    suspend fun getPlaceTagsCenter(query: String)=
        geometryApi.getGeometryTagsCenter(query)



}