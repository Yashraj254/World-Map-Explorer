package com.example.worldmapexplorer.data.network.api

import com.example.worldmapexplorer.data.network.dto.ElevationResponseDto
import com.skydoves.sandwich.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ElevationAPi {
    @GET("lookup")
    suspend fun getElevation(@Query("locations") locations: String): ApiResponse<ElevationResponseDto>
}