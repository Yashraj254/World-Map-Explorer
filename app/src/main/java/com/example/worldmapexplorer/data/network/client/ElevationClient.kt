package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.ElevationAPi
import javax.inject.Inject

class ElevationClient @Inject constructor(
    private val elevationAPi: ElevationAPi
) {

    suspend fun getElevation(lat: Double, lon: Double) = elevationAPi.getElevation("$lat,$lon")
}