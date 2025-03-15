package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.NodeGeometry
import com.example.worldmapexplorer.data.network.dto.PlaceDetails
import com.example.worldmapexplorer.data.network.dto.RelationGeometry
import com.example.worldmapexplorer.data.network.dto.WayGeometry
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GeometryApi {

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getWayGeometry(@Field("data") query: String): ApiResponse<WayGeometry>

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getRelationGeometry(@Field("data") query: String): ApiResponse<RelationGeometry>

    @FormUrlEncoded
    @POST("interpreter")
    suspend fun getNodeGeometry(@Field("data") query: String): ApiResponse<NodeGeometry>



}