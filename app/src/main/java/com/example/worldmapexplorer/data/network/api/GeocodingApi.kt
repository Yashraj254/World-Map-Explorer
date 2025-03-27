package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
//
//    @GET("search.php")
//    suspend fun getGeocoding(
//        @Query("q") q: String,
//        @Query("format") format: String,
//        @Query("exclude_place_ids") exclude_place_ids: String
//    ): ApiResponse<GeocodingResponseDto>

    @GET("reverse")
    suspend fun getPlaceBorders(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int,
        @Query("format") format: String,
        @Query("polygon_geojson") polygonGeojson: Int,
        @Query("polygon_threshold") polygonThreshold: Double
    ): ApiResponse<PlaceBordersDto>

    @GET("reverse")
    suspend fun getPlaceDetails(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int,
        @Query("format") format: String,
    ): ApiResponse<Place>


}


//https://nominatim.geocoding.ai/reverse?lat=23.383745114095703&lon=76.52978866927101&zoom=6&format=geojson&polygon_geojson=1&polygon_threshold=0.0004551661356395084

//https://nominatim.geocoding.ai/search.php?q=Ganges&format=jsonv2&exclude_place_ids=