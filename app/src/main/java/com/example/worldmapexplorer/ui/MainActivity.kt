package com.example.worldmapexplorer.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.worldmapexplorer.R
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.databinding.ActivityMainBinding
import com.example.worldmapexplorer.databinding.PlaceDetailsBottomSheetBinding
import com.example.worldmapexplorer.databinding.PlacesBottomSheetBinding
import com.example.worldmapexplorer.databinding.RouteDetailsBottomSheetBinding
import com.example.worldmapexplorer.utils.dpToPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.infowindow.InfoWindow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var placeDetailsBinding: PlaceDetailsBottomSheetBinding
    private lateinit var placesBinding: PlacesBottomSheetBinding
    private lateinit var routeDetailsBinding: RouteDetailsBottomSheetBinding
    private var selectedPlace: Place? = null
    private var isPolitical = true
    private lateinit var placeInfo: PlaceInfo
    private var searchRoute = false
    private lateinit var adapter: PlaceAdapter
    private var isLoading = false
    private lateinit var mapHandler: MapHandler
    private lateinit var currentLocation: GeoPoint
    private lateinit var borderDistances: Map<String, Float>
    private lateinit var startDestination: String
    private lateinit var endDestination: String

    // Cached bottom sheet behaviors for reuse
    private lateinit var placeDetailsBottomSheetBehavior: BottomSheetBehavior<CoordinatorLayout>
    private lateinit var placesBottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var routeDetailsBottomSheetBehavior: BottomSheetBehavior<CoordinatorLayout>

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                mapHandler.myLocationOverlay.enableMyLocation()
                mapHandler.myLocationOverlay.runOnFirstFix {
                    runOnUiThread { mapHandler.moveToCurrentLocation() }
                }
            } else {
                Toast.makeText(this, "Location permission denied!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        placeDetailsBinding = binding.includedPlaceInfoBottomSheet
        placesBinding = binding.includedPlacesBottomSheet
        routeDetailsBinding = binding.includedRouteDetailsBottomSheet
        setContentView(binding.root)


        requestLocationPermission()
        setupWindowInsets()
        setupUIListeners()
        observeViewModel()
        setupRecyclerView()
        setupBottomSheets()


        mapHandler = MapHandler(this, binding.mapView)
        mapHandler.setupMap()

        if (checkLocationPermission() && isGpsEnabled()) {
            mapHandler.myLocationOverlay.runOnFirstFix {
                runOnUiThread { mapHandler.moveToCurrentLocation() }
            }
        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isGpsEnabled()) {
                mapHandler.myLocationOverlay.enableMyLocation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        registerReceiver(locationReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupBottomSheets() {
        placeDetailsBottomSheetBehavior =
            BottomSheetBehavior.from(placeDetailsBinding.bottomSheetPlaceDetails)
        placesBottomSheetBehavior = BottomSheetBehavior.from(placesBinding.bottomSheetPlaces)
        routeDetailsBottomSheetBehavior =
            BottomSheetBehavior.from(routeDetailsBinding.bottomSheetRouteDetails)

        placeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        placesBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        routeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun setupUIListeners() {
        placesBinding.btnCloseSheet.setOnClickListener {
            hidePlaces()
        }
        placeDetailsBinding.btnCloseSheet.setOnClickListener {
            hidePlaceDetails()

        }
        routeDetailsBinding.btnCloseSheet.setOnClickListener {
            hideRouteDetails()
        }

        binding.etStartLocation.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etStartLocation.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard(v)
                    viewModel.fetchPlaces(query)
                    if (searchRoute)
                        placesBinding.tvHeading.text = "Select Start Destination"
                    else {
                        placesBinding.tvHeading.text = "Search Results"
                        hideRouteDetails()
                    }
                    showPlaces()
                }
                true
            } else {
                false
            }
        }

        binding.fabLayers.setOnClickListener {
            isPolitical = !isPolitical
            if (isPolitical) {
                binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
                Toast.makeText(this, "Switched to Political Map", Toast.LENGTH_SHORT).show()
            } else {
                mapHandler.setOpenTopoMap()
                Toast.makeText(this, "Switched to Geographical Map", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabLocation.setOnClickListener {
            if (checkLocationPermission()) {
                mapHandler.moveToCurrentLocation()
            } else {
                requestLocationPermission()
            }
        }

        binding.fabDistance.setOnClickListener {
            showPlaceDetailsDialog(
                "${borderDistances["north"]} km",
                "${borderDistances["south"]} km",
                "${borderDistances["east"]} km",
                "${borderDistances["west"]} km"
            )
        }

        placeDetailsBinding.btnDirections.setOnClickListener { v->
            binding.etDestination.visibility = View.VISIBLE
            binding.divider.visibility = View.VISIBLE
            binding.etDestination.setText(placeInfo.address)
            binding.etStartLocation.text.clear()
            binding.etStartLocation.setHint("Enter Start Location")
            binding.etStartLocation.requestFocus()
            searchRoute = true
            routeDetailsBinding.tvEndDestination.text = placeInfo.name
            hidePlaceDetails()
            showKeyboard(v)
//            placeDetailsBinding.bottomSheetPlaceDetails.visibility = View.GONE
        }

        // add marker on click event
        setupMapClickListener()
    }

    private fun setupMapClickListener() {
        val overlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                currentLocation = geoPoint
                Log.d("MapClick", "Clicked at: ${geoPoint.latitude}, ${geoPoint.longitude}")
                viewModel.getElevation(geoPoint.latitude, geoPoint.longitude)
                // Add marker at clicked location

                mapHandler.addMarker(geoPoint)
                InfoWindow.closeAllInfoWindowsOn(mapView)

                viewModel.getBorder(geoPoint.latitude, geoPoint.longitude, mapView.zoomLevel)

                return true
            }
        }

        if (!binding.mapView.overlays.contains(overlay)) {
            binding.mapView.overlays.add(overlay)
            InfoWindow.closeAllInfoWindowsOn(binding.mapView);

        }
    }


    private fun hidePlaceDetails() {
        placeDetailsBottomSheetBehavior.peekHeight = 0
        placeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.mapView.resetScrollableAreaLimitLatitude()
        binding.mapView.resetScrollableAreaLimitLongitude()
        mapHandler.removePolygon()
        viewModel.clearGeometry()
        viewModel.clearPlaceDetails()
    }

    private fun showPlaceDetails() {
        placeDetailsBottomSheetBehavior.peekHeight = 62.dpToPx(this)
        placeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        hidePlaces()
    }

    private fun hidePlaces() {
        placesBottomSheetBehavior.peekHeight = 0
        placesBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        viewModel.clearPlaces()
    }

    private fun showPlaces() {
        placesBottomSheetBehavior.peekHeight = 62.dpToPx(this)
        placesBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        hidePlaceDetails()
    }

    private fun hideRouteDetails() {
        routeDetailsBottomSheetBehavior.peekHeight = 0
        routeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        searchRoute = false
        mapHandler.removeRoute()
        viewModel.clearRoute()
    }

    private fun showRouteDetails() {
        routeDetailsBottomSheetBehavior.peekHeight = 62.dpToPx(this)
        routeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        binding.etStartLocation.clearFocus()
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }


    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.wayGeometry, viewModel.bounds) { points, bounds ->
                        points to bounds
                    }.collect { (points, bounds) ->
                        if (points.isNotEmpty()) {
                            Log.d("Polygon", "observeViewModel: $points")
                            mapHandler.drawPolygon(points, bounds)
                        }
                    }
                }
                launch {
                    viewModel.placeInfo.collect {
                        if (it != null) {
                            placeInfo = it
                        }
                        updatePlaceInfo(it)
                    }
                }

                launch {
                    viewModel.errorMessage.collect {
                        if (it != null) {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            viewModel.clearErrorMessage()
                        }
                    }
                }
                launch {
                    viewModel.altitude.collect {
                        Log.d("MainActivity", "observeViewModel: Altitude: $it")
                        if (it != null) {
                            binding.tvAltitude.text = "Altitude: $it m"
                            binding.tvAltitude.visibility = View.VISIBLE
                        } else {
                            binding.tvAltitude.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.places.collect {
                        Log.d("SearchResults", "onViewCreated: $it")
                        adapter.submitList(it)
                    }
                }
                launch {
                    viewModel.isLoading.collect {
                        isLoading = it
                        adapter.showLoadingIndicator(it)
                        placeDetailsBinding.pbPlaceDetails.isVisible = it
                        routeDetailsBinding.pbRouteDetails.isVisible = it
                        placeDetailsBinding.llPlaceDetails.isVisible = !it
                        routeDetailsBinding.llRouteDetails.isVisible = !it
                    }
                }

                launch {
                    viewModel.route.collect {
                        if (it.isNotEmpty()) {
                            mapHandler.drawRoute(it) }
                    }
                }

                launch {
                    viewModel.routeDetails.collect {
                        if (it != null) {
                            routeDetailsBinding.tvDistance.text = "Length: ${it.length}"
                            routeDetailsBinding.tvTime.text = "Time: ${it.time}"
                            binding.etDestination.visibility = View.GONE
                            binding.divider.visibility = View.GONE
                            binding.etStartLocation.text.clear()
                            binding.etStartLocation.setHint("Search Location")
                            searchRoute = false
                        }
                    }
                }

                launch {
                    viewModel.border.collect {
                        if (it.isNotEmpty()) {
                            mapHandler.drawBorder(it)
                            viewModel.calculateDistances(currentLocation, it)
                        }
                    }
                }
                launch {
                    viewModel.distances.collect {
                        if (it != null) {
                            binding.fabDistance.visibility = View.VISIBLE
                            borderDistances = it
                        } else {
                            binding.fabDistance.visibility = View.GONE
                        }
                        Log.d("Distances", "Distances: ${it?.entries}")
                    }
                }
            }
        }
    }

    private fun updatePlaceInfo(placeInfo: PlaceInfo?) {
        placeInfo?.let {
            Log.d("MainActivity", "placeInfo: ${it.address}")
            placeDetailsBinding.apply {
                tvName.text = it.name
                tvArea.text = "${it.area} km²"
                tvType.text = it.type
                tvAddress.text = it.address
            }
        }
    }

    private fun requestLocationPermission() {
        if (!checkLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaceAdapter() { selectedItem ->
            val place = PlaceInfo.Builder()
            place.setName(selectedItem.name)
            place.setType(selectedItem.type)
            place.setAddress(selectedItem.displayName)

            viewModel.selectedPlace = selectedItem

            if (searchRoute) {
                routeDetailsBinding.tvStartDestination.text = selectedItem.name

                viewModel.getRoute(
                    listOf(
                        LatLon(selectedItem.lat, selectedItem.lon),
                        LatLon(selectedPlace!!.lat, selectedPlace!!.lon)
                    )
                )
                selectedPlace = null
                hidePlaces()
                hidePlaceDetails()
                showRouteDetails()
            } else {
                viewModel.getGeometry(selectedItem.osmId, place, selectedItem.osmType)
                showPlaceDetails()
            }
            selectedPlace = selectedItem

            // Handle item click (e.g., move map to selected location)
        }
        placesBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        placesBinding.recyclerView.adapter = adapter
        placesBinding.recyclerView.addOnItemTouchListener(object :
            RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                placesBinding.recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                TODO("Not yet implemented")
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                TODO("Not yet implemented")
            }
        })

        placesBinding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (!isLoading && lastVisibleItem == totalItemCount - 1) {
                    viewModel.fetchMorePlaces() // Fetch more data only when the last item is fully visible
                }
            }
        })

    }

    fun showPlaceDetailsDialog(north: String, south: String, east: String, west: String) {
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.distance_dialog, null)

        val tvDistanceNorth: TextView = dialogView.findViewById(R.id.tvDistanceNorth)
        val tvDistanceSouth: TextView = dialogView.findViewById(R.id.tvDistanceSouth)
        val tvDistanceEast: TextView = dialogView.findViewById(R.id.tvDistanceEast)
        val tvDistanceWest: TextView = dialogView.findViewById(R.id.tvDistanceWest)
        val btnOk: Button = dialogView.findViewById(R.id.btn_ok)

        // Set distances
        tvDistanceNorth.text = "North: $north"
        tvDistanceSouth.text = "South: $south"
        tvDistanceEast.text = "East: $east"
        tvDistanceWest.text = "West: $west"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnOk.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }


}
