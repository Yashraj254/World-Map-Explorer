package com.example.worldmapexplorer.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.repository.PlacesRepository
import com.example.worldmapexplorer.utils.calculatePolygonArea
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.net.SocketException
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sin

@HiltViewModel
class MainViewModel @Inject constructor(private val placesRepository: PlacesRepository) :
    ViewModel() {

    private val _places: MutableStateFlow<List<Place>> = MutableStateFlow(emptyList())
    val places: StateFlow<List<Place>> = _places

    private val excludedPlaceIds = mutableSetOf<Long>()

    private val _searchQuery = MutableStateFlow("")

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _points: MutableStateFlow<List<GeoPoint>> = MutableStateFlow(emptyList())
    val points: StateFlow<List<GeoPoint>> = _points

    private val _placeInfo: MutableStateFlow<PlaceInfo?> = MutableStateFlow(null)
    val placeInfo: StateFlow<PlaceInfo?> = _placeInfo

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _altitude: MutableStateFlow<Double?> = MutableStateFlow(null)
    val altitude: StateFlow<Double?> = _altitude

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
            delay(2000) // ✅ Apply debounce (1 request per second)
            _isLoading.emit(true)
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

    fun getWayGeometry(osmId: Long, placeBuilder: PlaceInfo.Builder) = viewModelScope.launch {
        _placeInfo.emit(null)
        _isLoading.emit(true)
        val query = "[out:json][timeout:25];way($osmId);out geom;"
        placesRepository.getWayGeometry(query).suspendOnSuccess {
            Log.d("ViewModel", "getWayGeometry: ${data.elements[0].tags}")
            val points = data.elements[0].geometry.map {
                GeoPoint(it.lat, it.lon)
            }
            val latLng = points.map { Pair(it.latitude, it.longitude) }
            val area = calculatePolygonArea(latLng) // Returns area in square meters

            val prefix = placesRepository.fetchPrefix(data.elements[0].tags)
            placeBuilder.setType(prefix)
            placeBuilder.setArea(area.toFloat())
            val place = placeBuilder.build()
            Log.d("ViewModel", "Place: ${place.address}")
            _placeInfo.emit(place)
            _points.emit(points)
            _isLoading.emit(false)
            // Handle success
        }.suspendOnException {
            // Handle error
            _isLoading.emit(false)
            if (exception is SocketException) {
                _errorMessage.emit("Request timed out")
            } else {
                _errorMessage.emit("An error occurred")
            }
        }
    }

    fun getRoute() = viewModelScope.launch {
        _isLoading.emit(true)
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
}