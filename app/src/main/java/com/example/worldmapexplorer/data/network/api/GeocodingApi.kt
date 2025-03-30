package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {

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

