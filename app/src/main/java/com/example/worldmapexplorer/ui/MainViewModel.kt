package com.example.worldmapexplorer.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.network.dto.RouteDetails
import com.example.worldmapexplorer.data.repository.PlacesRepository
import com.example.worldmapexplorer.utils.calculateDistances
import com.example.worldmapexplorer.utils.calculatePolygonArea
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
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val placesRepository: PlacesRepository) :
    ViewModel() {

    private val _places: MutableStateFlow<List<Place>> = MutableStateFlow(emptyList())
    val places: StateFlow<List<Place>> = _places

    private val excludedPlaceIds = mutableSetOf<Long>()

    private val _searchQuery = MutableStateFlow("")

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading


    private val _wayGeometry: MutableStateFlow<List<GeoPoint>> = MutableStateFlow(emptyList())
    val wayGeometry: StateFlow<List<GeoPoint>> = _wayGeometry

    private val _relationGeometry: MutableStateFlow<List<List<GeoPoint>>> = MutableStateFlow(emptyList())
    val relationGeometry: StateFlow<List<List<GeoPoint>>> = _relationGeometry

    private val _nodeGeometry: MutableStateFlow<LatLon?> = MutableStateFlow(null)
    val nodeGeometry: StateFlow<LatLon?> = _nodeGeometry


    private val _bounds: MutableStateFlow<BoundingBox> = MutableStateFlow(BoundingBox())
    val bounds: StateFlow<BoundingBox> = _bounds

    private val _placeInfo: MutableStateFlow<PlaceInfo?> = MutableStateFlow(null)
    val placeInfo: StateFlow<PlaceInfo?> = _placeInfo

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _altitude: MutableStateFlow<Double?> = MutableStateFlow(null)
    val altitude: StateFlow<Double?> = _altitude

    private val _route: MutableStateFlow<List<GeoPoint>> = MutableStateFlow(emptyList())
    val route: StateFlow<List<GeoPoint>> = _route

    private val _routeDetails: MutableStateFlow<RouteDetails?> = MutableStateFlow(null)
    val routeDetails: StateFlow<RouteDetails?> = _routeDetails

    private val _border: MutableStateFlow<List<List<GeoPoint>>> = MutableStateFlow(emptyList())
    val border: StateFlow<List<List<GeoPoint>>> = _border

    private val _distances: MutableStateFlow<Map<String,Float>?> = MutableStateFlow(null)
    val distances: StateFlow<Map<String,Float>?> = _distances

    lateinit var selectedPlace: Place

    fun fetchPlaces(query: String) = viewModelScope.launch {
        _isLoading.emit(true)
        _searchQuery.value = query
        excludedPlaceIds.clear()

        placesRepository.fetchPlaces(query, "").suspendOnSuccess {
            _places.emit(data)
            excludedPlaceIds.addAll(data.map { it.placeId }) // ✅ Add IDs to exclusion list
            _isLoading.emit(false)
            delay(2000)
        }
    }

    private var searchJob: Job? = null

    fun fetchMorePlaces() {
        if (_places.value.isEmpty() || isLoading.value) return // ✅ Don't fetch if first batch is empty

        searchJob?.cancel() // 🚀 Cancel previous job (Debounce)

        searchJob = viewModelScope.launch {
            _isLoading.emit(true)
            delay(2000) // ✅ Apply debounce (1 request per second)
            val excludedIds = excludedPlaceIds.joinToString(",")

            placesRepository.fetchPlaces(_searchQuery.value, excludedIds)
                .suspendOnSuccess {
                    if (data.isNotEmpty()) {
                        excludedPlaceIds.addAll(data.map { it.placeId }) // ✅ Exclude already fetched IDs
                        _places.update { it + data }
                    }
                    _isLoading.emit(false)
                }
        }
    }

    fun getGeometry(osmId: Long, placeBuilder: PlaceInfo.Builder,osmType: String) = viewModelScope.launch {
        _placeInfo.emit(null)
        _isLoading.emit(true)
        val query = "[out:json][timeout:25];$osmType($osmId);out geom;"
        Log.d("TAG", "getGeometry: $osmType")
        when(osmType){
            "way"->{
                placesRepository.getWayGeometry(query).suspendOnSuccess {
                    Log.d("ViewModel", "getWayGeometry: ${data.elements[0].tags}")
                    val points = data.elements[0].geometry.map {
                        GeoPoint(it.lat, it.lon)
                    }
                    val bounds = data.elements[0].bounds
                    val latLng = points.map { Pair(it.latitude, it.longitude) }
                    val area = calculatePolygonArea(latLng) // Returns area in square meters

                    val prefix = placesRepository.fetchPrefix(data.elements[0].tags)
                    placeBuilder.setType(prefix)
                    placeBuilder.setArea(area.toFloat())
                    val place = placeBuilder.build()
                    Log.d("ViewModel", "Place: ${place.address}")
                    _placeInfo.emit(place)
                    _wayGeometry.emit(points)
                    _bounds.emit(
                        BoundingBox(
                            bounds.maxLat,
                            bounds.maxLon,
                            bounds.minLat,
                            bounds.minLon
                        )
                    )
                    _isLoading.emit(false)
                }

            }
            "node"->{
//                placesRepository.getNodeGeometry(query).suspendOnSuccess {
//                    val point = LatLon(data.elements[0].latitude, data.elements[0].longitude)
//                    _nodeGeometry.emit(point)
//                }
            }
            "relation"->{

            }
        }

    }

    fun getRoute(locations: List<LatLon>) = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.emit(true)
        placesRepository.getRoute(locations).suspendOnSuccess {
            val decodedRoute = decodePolyline(data.trip.legs[0].shape)
            _route.emit(decodedRoute)
            _routeDetails.emit(RouteDetails(data.trip.summary.length.toInt().toString(), convertSeconds(data.trip.summary.time)))
            _isLoading.emit(false)
        }
//        placesRepository.getRoute()
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
            calculateDistances(GeoPoint(lat,lon),_border.value)

            _border.value.forEach {
                if (isPointInPolygon(GeoPoint(lat,lon),it)){
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

    private fun parseCoordinates(coordinates: List<*>, type: String): List<List<GeoPoint>> {

        return when (type) {
            "Polygon" -> listOf(parsePolygon(coordinates))
            "MultiPolygon" -> coordinates.mapNotNull { parsePolygon(it as? List<*>) }
            else -> emptyList()
        }
    }

    private fun parsePolygon(coordinates: List<*>?): List<GeoPoint> {
        return coordinates?.firstOrNull()?.let { firstRing ->
            (firstRing as? List<*>)?.mapNotNull {
                (it as? List<*>)?.takeIf { it.size >= 2 }
                    ?.let { GeoPoint(it[1] as Double, it[0] as Double) }
            }
        } ?: emptyList()
    }

    fun calculateDistances(marker: GeoPoint,polygons:List<List<GeoPoint>>) = viewModelScope.launch {
        val borderPoints = findBorderPoints(marker,polygons)
        val distances = borderPoints.calculateDistances(marker)
        _distances.emit(distances)
    }

    fun clearGeometry() = viewModelScope.launch {
        _relationGeometry.emit(emptyList())
        _wayGeometry.emit(emptyList())
        _nodeGeometry.emit(null)
    }

    fun clearRoute() = viewModelScope.launch {
        _route.emit(emptyList())
    }

    fun clearPlaces() = viewModelScope.launch {
        _places.emit(emptyList())
    }

    fun clearPlaceDetails() = viewModelScope.launch {
        _placeInfo.emit(null)
    }
}

