package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.PlaceDetails
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GeometryApi {

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getWayGeometry(@Field("data") query: String): ApiResponse<PlaceDetails>

}