package com.example.worldmapexplorer.data.network.api

import com.skydoves.sandwich.ApiResponse
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GeometryApi {

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getGeometry(@Field("data") query: String): ApiResponse<ResponseBody>

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getGeometryTagsCenter(@Field("data") query: String): ApiResponse<ResponseBody>


}