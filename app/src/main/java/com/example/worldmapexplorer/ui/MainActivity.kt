package com.example.worldmapexplorer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.worldmapexplorer.R
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.databinding.ActivityMainBinding
import com.example.worldmapexplorer.databinding.FragmentPlaceDetailsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.internal.ViewUtils.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex.getX
import org.osmdroid.util.MapTileIndex.getY
import org.osmdroid.util.MapTileIndex.getZoom
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomSheetBinding: FragmentPlaceDetailsBottomSheetBinding
    private var isPolitical = true
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var placeInfo: PlaceInfo
    private var searchRoute = false

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                myLocationOverlay.enableMyLocation()
                myLocationOverlay.runOnFirstFix {
                    runOnUiThread { moveToCurrentLocation() }
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
        bottomSheetBinding = binding.includedBottomSheet
        setContentView(binding.root)

        setupMap()

        requestLocationPermission()
        setupWindowInsets()
        setupBottomSheet()
        setupUIListeners()
        observeViewModel()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        myLocationOverlay = MyLocationNewOverlay(map).apply {
            enableMyLocation()
//            runOnFirstFix {
//                runOnUiThread { moveToCurrentLocation() }
//            }
        }

        map.overlays.add(myLocationOverlay)

        // Start with Delhi, India as the default location until permission is granted
        val defaultLocation = GeoPoint(28.6139, 77.2090) // Delhi, India
        map.controller.setZoom(15.0)


//        if (checkLocationPermission()) {
//            val currentLocation = myLocationOverlay.myLocation
//            if (currentLocation != null) {
//                map.controller.setCenter(currentLocation)
//            } else {
//                map.controller.setCenter(defaultLocation)
//            }
//        } else {
//            map.controller.setCenter(defaultLocation)
//        }
        map.controller.setCenter(defaultLocation)
        if (checkLocationPermission()) {
            lifecycleScope.launch {
                myLocationOverlay.runOnFirstFix {
                    runOnUiThread {
                        moveToCurrentLocation()
                    }
                }
//                delay(3000) // Give time for location to be acquired
//                val currentLocation = myLocationOverlay.myLocation
//                if (currentLocation != null) {
//                    moveToCurrentLocation()
//                }
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun moveToCurrentLocation() {
        myLocationOverlay.myLocation?.let { geoPoint ->
            binding.mapView.controller.apply {
                setZoom(20.0)
                setCenter(geoPoint)
                animateTo(geoPoint)
            }
        } ?: Toast.makeText(this, "Location not found. Ensure GPS is enabled!", Toast.LENGTH_SHORT)
            .show()
    }

    private fun setupBottomSheet() {
        val bottomSheetBehavior =
            BottomSheetBehavior.from(bottomSheetBinding.bottomSheetPlaceDetails)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupUIListeners() {
        binding.etStartLocation.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etStartLocation.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard(v)
                    viewModel.fetchPlaces(query)
                    SearchResultsBottomSheetFragment(searchRoute).show(
                        supportFragmentManager,
                        "SearchResults"
                    )
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
                setOpenTopoMap()
                Toast.makeText(this, "Switched to Geographical Map", Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabLocation.setOnClickListener {
            if (checkLocationPermission()) {
                moveToCurrentLocation()
            } else {
                requestLocationPermission()
            }
        }

        bottomSheetBinding.btnDirections.setOnClickListener {
            binding.etDestination.visibility = View.VISIBLE
            binding.divider.visibility = View.VISIBLE
            binding.etDestination.setText(placeInfo.address)
            binding.etStartLocation.text.clear()
            binding.etStartLocation.setHint("Enter Start Location")
            binding.etStartLocation.requestFocus()
            bottomSheetBinding.bottomSheetPlaceDetails.visibility = View.GONE
        }

        // add marker on click event
        setupMapClickListener()
    }

    private fun setupMapClickListener() {
        val overlay = object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint

                Log.d("MapClick", "Clicked at: ${geoPoint.latitude}, ${geoPoint.longitude}")
                viewModel.getElevation(geoPoint.latitude, geoPoint.longitude)
                // Add marker at clicked location
                addMarker(geoPoint)

                return true
            }
        }

        // Remove any existing click listeners
        binding.mapView.overlays.removeIf { it is Overlay }
        binding.mapView.overlays.add(overlay)
    }


    private fun addMarker(location: GeoPoint) {
        val marker = Marker(binding.mapView)
        marker.position = location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_marker) // Custom marker icon

        // Optional: Show info window when clicked
        marker.title = "Selected Location"
        marker.snippet = "Lat: ${location.latitude}, Lng: ${location.longitude}"
        marker.setOnMarkerClickListener { m, _ ->
            m.showInfoWindow()
            true
        }

        // Remove previous markers if needed
        binding.mapView.overlays.removeIf { it is Marker }

        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate() // Refresh the map
    }


    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        binding.etStartLocation.clearFocus()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.points.collect { if (it.isNotEmpty()) drawPolygon(it) } }
                launch { viewModel.placeInfo.collect {
                    if (it != null) {
                        placeInfo = it
                    }
                    updatePlaceInfo(it) } }
                launch {
                    viewModel.isLoading.collect {
                        binding.pbLoading.visibility = if (it) View.VISIBLE else View.GONE
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
                        if (it!=null){
                            binding.tvAltitude.text = "Altitude: $it m"
                            binding.tvAltitude.visibility = View.VISIBLE
                        } else {
                            binding.tvAltitude.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun updatePlaceInfo(placeInfo: PlaceInfo?) {
        placeInfo?.let {
            Log.d("MainActivity", "placeInfo: ${it.address}")
            bottomSheetBinding.apply {
                tvName.text = it.name
                tvArea.text = "${it.area} km²"
                tvType.text = it.type
                tvAddress.text = it.address
            }
            binding.bottomSheet.visibility = View.VISIBLE
        }
    }

    private fun drawPolygon(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            Log.e("Geopoints", "No points available to draw a polygon!")
            return
        }

        val polygon = Polygon(binding.mapView).apply {
            this.points = points
            fillColor = Color.argb(128, 255, 255, 0) // Yellow Transparent
            strokeColor = Color.RED
            strokeWidth = 3f
        }

        binding.mapView.overlayManager.apply {
            overlays().clear()
            add(myLocationOverlay)
            add(polygon)
        }

        binding.mapView.controller.apply {
            setCenter(points.first())
            setZoom(18.0)
        }
        binding.mapView.invalidate()
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            myLocationOverlay.enableMyLocation()
        }
    }


    private fun setOpenTopoMap() {
        val openTopoMap = object : OnlineTileSourceBase(
            "OpenTopoMap", 0, 18, 256, "",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return "$baseUrl${getZoom(pMapTileIndex)}/${getX(pMapTileIndex)}/${getY(pMapTileIndex)}.png"
            }
        }
        binding.mapView.setTileSource(openTopoMap)
    }
}
