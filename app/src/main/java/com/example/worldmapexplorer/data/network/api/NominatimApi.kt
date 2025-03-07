package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.Place
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {

    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("exclude_place_ids", encoded = true) excluded: String,
        @Query("format") format: String = "jsonv2",
    ): ApiResponse<List<Place>>
}