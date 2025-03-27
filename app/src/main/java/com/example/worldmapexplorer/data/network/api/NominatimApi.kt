package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.Place
import com.skydoves.sandwich.ApiResponse
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {

    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("exclude_place_ids", encoded = true) excluded: String,
        @Query("format") format: String = "jsonv2",
    ): ApiResponse<List<Place>>

    @GET("details")
    suspend fun placeDetails(
        @Query("osmtype") osmtype: String,
        @Query("osmid") osmid: Long,
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("format") format: String = "json",
    ): ApiResponse<ResponseBody>

//    https://nominatim.openstreetmap.com/details?osmtype=R&osmid=1976132&addressdetails=1&format=json
}