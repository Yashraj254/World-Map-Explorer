package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.RouterApi
import com.example.worldmapexplorer.data.network.dto.RouteRequestDto
import javax.inject.Inject

class RouteClient @Inject constructor(
    private val routerApi: RouterApi
) {

    suspend fun getRoute(routeRequestDto: RouteRequestDto) = routerApi.getRoute(routeRequestDto)
}