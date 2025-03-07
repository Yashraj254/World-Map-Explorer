package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.RouteRequestDto
import com.example.worldmapexplorer.data.network.dto.RouteResponseDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface RouterApi {

    @POST("route")
    suspend fun getRoute(@Body routeRequestDto: RouteRequestDto): ApiResponse<RouteResponseDto>
}