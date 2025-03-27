package com.example.worldmapexplorer.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.worldmapexplorer.data.models.PlaceDetails
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.network.dto.RouteDetails
import com.example.worldmapexplorer.data.repository.GeoJsonGeometry
import com.example.worldmapexplorer.data.repository.PlacesRepository
import com.example.worldmapexplorer.utils.Coordinates
import com.example.worldmapexplorer.utils.calculateDistances
import com.example.worldmapexplorer.utils.convertSeconds
import com.example.worldmapexplorer.utils.decodePolyline
import com.example.worldmapexplorer.utils.findBorderPoints
import com.example.worldmapexplorer.utils.isPointInPolygon
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val placesRepository: PlacesRepository) :
    ViewModel() {

    private val _places: MutableStateFlow<List<Place>> = MutableStateFlow(emptyList())
    val places: StateFlow<List<Place>> = _places

    private val _placeDetails: MutableStateFlow<PlaceDetails?> = MutableStateFlow(null)
    val placeDetails: StateFlow<PlaceDetails?> = _placeDetails

    private val excludedPlaceIds = mutableSetOf<Long>()

    private val _searchQuery = MutableStateFlow("")

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _geometry: MutableStateFlow<GeoJsonGeometry?> = MutableStateFlow(null)
    val geometry: StateFlow<GeoJsonGeometry?> = _geometry


    private val _bounds: MutableStateFlow<BoundingBox> = MutableStateFlow(BoundingBox())
    val bounds: StateFlow<BoundingBox> = _bounds

    private val _placeInfo: MutableStateFlow<PlaceInfo?> = MutableStateFlow(null)
    val placeInfo: StateFlow<PlaceInfo?> = _placeInfo

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _altitude: MutableStateFlow<Double?> = MutableStateFlow(null)
    val altitude: StateFlow<Double?> = _altitude

    private val _route: MutableStateFlow<List<Coordinates>> = MutableStateFlow(emptyList())
    val route: StateFlow<List<Coordinates>> = _route

    private val _routeDetails: MutableStateFlow<RouteDetails?> = MutableStateFlow(null)
    val routeDetails: StateFlow<RouteDetails?> = _routeDetails

    private val _border: MutableStateFlow<List<List<Coordinates>>> = MutableStateFlow(emptyList())
    val border: StateFlow<List<List<Coordinates>>> = _border

    private val _distances: MutableStateFlow<Map<String, Float>?> = MutableStateFlow(null)
    val distances: StateFlow<Map<String, Float>?> = _distances

    fun fetchPlaces(query: String) = viewModelScope.launch {
        _isLoading.emit(true)
        _searchQuery.value = query
        excludedPlaceIds.clear()

        placesRepository.fetchPlaces(query, "").suspendOnSuccess {
            _places.emit(data)
            excludedPlaceIds.addAll(data.map { it.placeId }) //  Add IDs to exclusion list
            _isLoading.emit(false)
            delay(2000)
        }
    }

    private var searchJob: Job? = null

    fun fetchMorePlaces() {
        if (_places.value.isEmpty() || isLoading.value) return

        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _isLoading.emit(true)
            delay(2000)
            val excludedIds = excludedPlaceIds.joinToString(",")

            placesRepository.fetchPlaces(_searchQuery.value, excludedIds)
                .suspendOnSuccess {
                    if (data.isNotEmpty()) {
                        excludedPlaceIds.addAll(data.map { it.placeId })
                        _places.update { it + data }
                    }
                    _isLoading.emit(false)
                }
        }
    }


    fun getActualGeometry(osmId: Long,placeBuilder: PlaceInfo.Builder, osmType: String) =
        viewModelScope.launch {
            _placeInfo.emit(null)
            _isLoading.emit(true)
            val query = "[out:json][timeout:25];$osmType($osmId);out geom;"
            placesRepository.getGeometry(query).suspendOnSuccess {
                _geometry.emit(data)
                getActualPlaceDetails(osmId, placeBuilder, osmType)

            }
        }

    fun getActualPlaceDetails(osmId: Long, placeBuilder: PlaceInfo.Builder, osmType: String) =
        viewModelScope.launch {
            val place = placesRepository.getPlaceDetailsForWiki(osmType,placeBuilder.build(), osmId)
            _placeDetails.emit(place)
            _isLoading.emit(false)
        }

    fun getPlaceDetails(lat: Double, lon: Double, zoom: Int) = viewModelScope.launch {
        placesRepository.getPlaceDetails(lat, lon, zoom).suspendOnSuccess {
            val placeInfo = PlaceInfo.Builder()
            placeInfo.setName(data.name)
            placeInfo.setType(data.type)
            placeInfo.setAddress(data.displayName)
            getActualGeometry(data.osmId,placeInfo, data.osmType)
        }
    }


    fun getRoute(locations: List<LatLon>) = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.emit(true)
        placesRepository.getRoute(locations).suspendOnSuccess {
            val decodedRoute = decodePolyline(data.trip.legs[0].shape)
            _route.emit(decodedRoute)
            _routeDetails.emit(
                RouteDetails(
                    data.trip.summary.length.toInt().toString(),
                    convertSeconds(data.trip.summary.time)
                )
            )
            _isLoading.emit(false)
        }
    }

    fun getElevation(lat: Double, lon: Double) = viewModelScope.launch {
        placesRepository.getElevation(lat, lon).suspendOnSuccess {
            _altitude.emit(data.results[0].elevation)
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getBorder(lat: Double, lon: Double, zoom: Int) {

        searchJob?.cancel() // 🚀 Cancel previous job (Debounce)

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            calculateDistances(Coordinates(lat, lon), _border.value)

            _border.value.forEach {
                if (isPointInPolygon(Coordinates(lat, lon), it)) {
                    return@launch
                }
            }

            delay(2000) // ✅ Apply debounce (1 request in 2 seconds)
            placesRepository.getPlacesBorder(lat, lon, zoom)
                .suspendOnSuccess {
                    val coordinates = data.features[0].geometry.coordinates
                    val nestedList = parseCoordinates(coordinates, data.features[0].geometry.type)
                    _border.emit(nestedList)
                }
        }
    }

    private fun parseCoordinates(coordinates: List<*>, type: String): List<List<Coordinates>> {

        return when (type) {
            "Polygon" -> listOf(parsePolygon(coordinates))
            "MultiPolygon" -> coordinates.mapNotNull { parsePolygon(it as? List<*>) }
            else -> emptyList()
        }
    }

    private fun parsePolygon(coordinates: List<*>?): List<Coordinates> {
        return coordinates?.firstOrNull()?.let { firstRing ->
            (firstRing as? List<*>)?.mapNotNull {
                (it as? List<*>)?.takeIf { it.size >= 2 }
                    ?.let { Coordinates(it[1] as Double, it[0] as Double) }
            }
        } ?: emptyList()
    }

    fun calculateDistances(marker: Coordinates, polygons: List<List<Coordinates>>) =
        viewModelScope.launch {
            val borderPoints = findBorderPoints(marker, polygons)
            val distances = borderPoints.calculateDistances(marker)
            _distances.emit(distances)
        }

    fun clearRoute() = viewModelScope.launch {
        _route.emit(emptyList())
    }

    fun clearPlaces() = viewModelScope.launch {
        _places.emit(emptyList())
    }

    fun clearBorder() = viewModelScope.launch {
        _border.emit(emptyList())
    }

    fun clearPlaceDetails() = viewModelScope.launch {
        _placeInfo.emit(null)
        _placeDetails.emit(null)
    }
}

