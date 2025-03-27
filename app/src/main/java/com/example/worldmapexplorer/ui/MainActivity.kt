package com.example.worldmapexplorer.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
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
import com.example.worldmapexplorer.data.models.CountryDetails
import com.example.worldmapexplorer.data.models.DistrictDetails
import com.example.worldmapexplorer.data.models.OtherAreaDetails
import com.example.worldmapexplorer.data.models.RiverDetails
import com.example.worldmapexplorer.data.models.StateDetails
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.repository.GeoJsonGeometry
import com.example.worldmapexplorer.databinding.ActivityMainBinding
import com.example.worldmapexplorer.databinding.PlaceDetailsBottomSheetBinding
import com.example.worldmapexplorer.databinding.PlacesBottomSheetBinding
import com.example.worldmapexplorer.databinding.RouteDetailsBottomSheetBinding
import com.example.worldmapexplorer.utils.Coordinates
import com.example.worldmapexplorer.utils.TemplateSection
import com.example.worldmapexplorer.utils.countryTemplate
import com.example.worldmapexplorer.utils.districtTemplate
import com.example.worldmapexplorer.utils.dpToPx
import com.example.worldmapexplorer.utils.otherAreaTemplate
import com.example.worldmapexplorer.utils.riverTemplate
import com.example.worldmapexplorer.utils.stateTemplate
import com.example.worldmapexplorer.utils.toGeoPoint
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

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
    private var currentLocation: GeoPoint? = null
    private lateinit var borderDistances: Map<String, Float>

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
        initializeBindings()
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

    private fun initializeBindings() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        placeDetailsBinding = binding.includedPlaceInfoBottomSheet
        placesBinding = binding.includedPlacesBottomSheet
        routeDetailsBinding = binding.includedRouteDetailsBottomSheet
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

    private fun isGpsEnabled() = (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER)

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
                handleLocationSearch(v)
                true
            } else {
                false
            }
        }


        placeDetailsBinding.btnDirections.setOnClickListener { v ->
            setupDirectionsUI()
            showKeyboard(v)
//            placeDetailsBinding.bottomSheetPlaceDetails.visibility = View.GONE
        }
        setUpFabListeners()
        // add marker on click event
        setupMapClickListener()
    }

    private fun setUpFabListeners() {
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

    }

    private fun setupDirectionsUI() {
        binding.etDestination.visibility = View.VISIBLE
        binding.divider.visibility = View.VISIBLE
        binding.etDestination.setText(placeInfo.displayName)
        binding.etStartLocation.text.clear()
        binding.etStartLocation.setHint("Enter Start Location")
        binding.etStartLocation.requestFocus()
        searchRoute = true
        routeDetailsBinding.tvEndDestination.text = placeInfo.name
        hidePlaceDetails()
    }

    private fun handleLocationSearch(view: View) {
        val query = binding.etStartLocation.text.toString().trim()
        if (query.isNotEmpty()) {
            hideKeyboard(view)
            viewModel.fetchPlaces(query)

            placesBinding.tvHeading.text = if (searchRoute) {
                "Select Start Destination"
            } else {
                hideRouteDetails()
                "Search Results"
            }

            showPlaces()
        }
    }

    private fun setupMapClickListener() {
        val overlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                currentLocation = geoPoint
                Log.d("MapClick", "Clicked at: ${geoPoint.latitude}, ${geoPoint.longitude}")
                viewModel.getElevation(geoPoint.latitude, geoPoint.longitude)
                mapHandler.addMarker(geoPoint)
                viewModel.clearBorder()
                viewModel.getBorder(geoPoint.latitude, geoPoint.longitude, mapView.zoomLevel)

                return true
            }

            override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                currentLocation = geoPoint
                Log.d("MapClick", "Clicked at: ${geoPoint.latitude}, ${geoPoint.longitude}")
                viewModel.getPlaceDetails(geoPoint.latitude, geoPoint.longitude, mapView.zoomLevel)
                showPlaceDetails()
                hideRouteDetails()
                return true
            }
        }

        if (!binding.mapView.overlays.contains(overlay)) {
            binding.mapView.overlays.add(overlay)
        }
    }


    private fun hidePlaceDetails() {
        placeDetailsBottomSheetBehavior.peekHeight = 0
        placeDetailsBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.mapView.resetScrollableAreaLimitLatitude()
        binding.mapView.resetScrollableAreaLimitLongitude()
        mapHandler.removePolygon()
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

    private fun populateTemplate(container: LinearLayout, template: List<TemplateSection>, dataMap: Map<String, String?>) {
        container.removeAllViews()

        template.forEach { section ->
            if (section.type == "header") {
                val headerView = TextView(this).apply {
                    text = section.text
                    textSize = when (section.level) {
                        3 -> 20f
                        5 -> 18f
                        else -> 16f
                    }
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 16, 0, 8)
                }
                container.addView(headerView)
            } else if (section.type == "list" || section.type == "paragraph") {
                section.items?.forEach { item ->
                    val value = dataMap[item.key] ?: "N/A" // Get value from map

                    val itemView = TextView(this).apply {
                        text = "${item.label}: $value" // Format label + value
                        textSize = 16f
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(itemView)
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                    launch {

                        viewModel.geometry.collect {
                            when (it) {
                                is GeoJsonGeometry.Polygon -> mapHandler.drawPolygon(it.coordinates.map { it.map { GeoPoint(it.latitude,it.longitude) } })
                                is GeoJsonGeometry.MultiPolygon -> mapHandler.drawMultiPolygon(it.coordinates.map { it.map { it.map { GeoPoint(it.latitude,it.longitude) } } })
                                is GeoJsonGeometry.LineString -> mapHandler.drawPolyline(it.coordinates.map { GeoPoint(it.latitude,it.longitude) })
                                is GeoJsonGeometry.MultiLineString -> mapHandler.drawMultiPolyline(it.coordinates.map { it.map { GeoPoint(it.latitude,it.longitude) } })
                                else -> {}
                            }
                        }
                    }

                    launch {
                        viewModel.placeInfo.collect {
                            if (it != null) {
//                            populateTemplate(placeDetailsBinding.llPlaceDetails, districtTemplate) // Change template as needed

                                placeInfo = it
                            }
//                            updatePlaceInfo(it)
                        }
                    }


                    launch {
                        viewModel.altitude.collect {
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
                            adapter.showShowMoreButton(true)
                        }
                    }
                    launch {
                        viewModel.isLoading.collect {

                            isLoading = it
                            adapter.showLoadingIndicator(it)
                            adapter.showShowMoreButton(!it)
                            placeDetailsBinding.pbPlaceDetails.isVisible = it
                            routeDetailsBinding.pbRouteDetails.isVisible = it
                            placeDetailsBinding.llPlaceDetailsContainer.isVisible = !it
                            routeDetailsBinding.llRouteDetails.isVisible = !it
                        }
                    }

                    launch {
                        viewModel.route.collect {
                            if (it.isNotEmpty()) {
                                mapHandler.drawRoute(it.map { GeoPoint(it.latitude,it.longitude) })
                            }
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
                        viewModel.border.collect { border ->

                            if (border.isNotEmpty()) {
                                mapHandler.drawBorder(border.map { it.map { it.toGeoPoint() } })
                                currentLocation?.let {
                                    viewModel.calculateDistances(Coordinates(it.latitude,it.longitude), border)
                                }
                            }
                            binding.fabDistance.isVisible = border.isNotEmpty()

                        }
                    }
                    launch {
                        viewModel.distances.collect {
                            if (it != null) {
                                borderDistances = it
                            }
                        }
                    }
                    launch {
                        viewModel.placeDetails.collect {
                            it?.let {
                                when (it) {
                                    is CountryDetails -> {
                                        val dataMap = mapOf(
                                            "capital" to it.capital,
                                            "continent" to it.continent,
                                            "coordinates" to it.coordinates,
                                            "area" to it.area,
                                            "language" to it.language,
                                            "population" to it.population,
                                            "borders" to it.borders
                                        )
                                        placeDetailsBinding.tvHeading.text = it.name
                                        populateTemplate(placeDetailsBinding.llPlaceDetails, countryTemplate, dataMap)
                                    }

                                    is StateDetails -> {
                                        val dataMap = mapOf(
                                            "country" to it.country,
                                            "capital" to it.capital,
                                            "coordinates" to it.coordinates,
                                            "area" to it.area,
                                            "borders" to it.borders,
                                            "summary" to it.summary
                                        )
                                        placeDetailsBinding.tvHeading.text = it.name
                                        populateTemplate(placeDetailsBinding.llPlaceDetails, stateTemplate, dataMap)
                                    }

                                    is DistrictDetails -> {
                                        val dataMap = mapOf(
                                            "state" to it.state,
                                            "coordinates" to it.coordinates,
                                            "area" to it.area,
                                            "borders" to it.borders,
                                            "summary" to it.summary
                                        )
                                        placeDetailsBinding.tvHeading.text = it.name
                                        populateTemplate(placeDetailsBinding.llPlaceDetails, districtTemplate, dataMap)
                                    }

                                    is RiverDetails -> {
                                        val dataMap = mapOf(
                                            "length" to it.length,
                                            "origin" to it.origin,
                                            "mouth" to it.mouth,
                                            "tributaries" to it.tributaries
                                        )
                                        placeDetailsBinding.tvHeading.text = it.name
                                        populateTemplate(placeDetailsBinding.llPlaceDetails, riverTemplate, dataMap)

                                    }

                                    is OtherAreaDetails -> {
                                        val dataMap = mapOf(
                                            "type" to it.type,
                                            "area" to it.area,
                                            "address" to it.address
                                        )
                                        placeDetailsBinding.tvHeading.text = it.name
                                        populateTemplate(placeDetailsBinding.llPlaceDetails, otherAreaTemplate, dataMap)

                                    }

                                }
                        }
                    }
                }
            }
        }
    }

//    private fun updatePlaceInfo(placeInfo: PlaceInfo?) {
//        placeInfo?.let {
//            placeDetailsBinding.apply {
//                tvName.text = it.name
//                tvArea.text = "${it.area} km²"
//                tvType.text = it.type
//                tvAddress.text = it.displayName
//            }
//        }
//    }

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
        adapter = PlaceAdapter ({ selectedItem ->
            handlePlaceSelection(selectedItem)
        }, onShowMoreClick = {
            viewModel.fetchMorePlaces()
        }
        )
        placesBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        placesBinding.recyclerView.adapter = adapter

        placesBinding.recyclerView.addOnItemTouchListener(object :
            RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                placesBinding.recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

    }

    private fun handlePlaceSelection(selectedItem: Place) {
        val place = PlaceInfo.Builder()
            .setName(selectedItem.name)
            .setType(selectedItem.type)
            .setAddress(selectedItem.displayName)

        placeInfo = place.build()
//        viewModel.selectedPlace = selectedItem

        if (searchRoute) {

            processRouteRequest(selectedItem)
        } else {
            viewModel.getActualGeometry(selectedItem.osmId, place, selectedItem.osmType)
//            viewModel.getActualPlaceDetails(selectedItem.osmId,place, selectedItem.osmType)
            showPlaceDetails()
        }
        selectedPlace = selectedItem

    }

    private fun processRouteRequest(startLocation: Place) {
        routeDetailsBinding.tvStartDestination.text = startLocation.name

        val destination = selectedPlace ?: return
        viewModel.getRoute(
            listOf(
                LatLon(destination.lat, destination.lon),
                LatLon(startLocation.lat, startLocation.lon)
            )
        )
        selectedPlace = null
        hidePlaces()
        hidePlaceDetails()
        showRouteDetails()
    }

    private fun showPlaceDetailsDialog(north: String, south: String, east: String, west: String) {
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
