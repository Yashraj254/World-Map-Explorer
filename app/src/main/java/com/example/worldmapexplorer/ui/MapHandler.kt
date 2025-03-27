package com.example.worldmapexplorer.ui

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.worldmapexplorer.R
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex.getX
import org.osmdroid.util.MapTileIndex.getY
import org.osmdroid.util.MapTileIndex.getZoom
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.max
import kotlin.math.min

class MapHandler(
    private val context: Context,
    private val mapView: MapView
) {
    lateinit var myLocationOverlay: MyLocationNewOverlay
    private var currentBorders: List<Polygon>? = null
    private var currentRivers: List<Polyline>? = null
    private var route: Polyline? = null
    var place: Polygon? = null
    private var currentMarker: Marker? = null  // Store last marker reference
    private lateinit var startMarker: Marker
    private lateinit var endMarker: Marker
    private var node: Marker? = null

    //    private var polygon: Polygon? = null
    private var polygons: ArrayList<Polygon> = ArrayList()
    private var polylines: ArrayList<Polyline> = ArrayList()

    fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        myLocationOverlay = MyLocationNewOverlay(mapView).apply {
            enableMyLocation()
        }

        mapView.overlays.add(myLocationOverlay)

        val defaultLocation = GeoPoint(28.6139, 77.2090) // Delhi, India
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(defaultLocation)
    }

    fun moveToCurrentLocation() {
        if (::myLocationOverlay.isInitialized && !myLocationOverlay.isMyLocationEnabled)
            myLocationOverlay.enableMyLocation()
        myLocationOverlay.myLocation?.let { geoPoint ->
            mapView.controller.apply {
                setZoom(20.0)
                setCenter(geoPoint)
                animateTo(geoPoint)
            }
        } ?: Toast.makeText(
            context,
            "Location not found. Ensure GPS is enabled!",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun addMarker(location: GeoPoint) {
        currentMarker?.let { mapView.overlays.remove(it) }
        mapView.overlays.removeIf { it is Marker }

        val marker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_marker)
            infoWindow = null
            setOnMarkerClickListener { marker, mapView -> true }
        }

        mapView.overlays.add(marker)
        currentMarker = marker
        mapView.invalidate()
    }


    fun addNode(location: GeoPoint) {
        node?.let { mapView.overlays.remove(it) }
        mapView.overlays.removeIf { it is Marker }

        val marker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_node)
            infoWindow = null
            setOnMarkerClickListener { marker, mapView -> true }
        }

        mapView.overlays.add(marker)
        node = marker
        mapView.invalidate()
    }

    fun drawPolygon(points: List<GeoPoint>, boundingBox: BoundingBox) {
        val padding = 0.0005  // Adjust this value for more/less padding

        if (points.isEmpty()) {
            return
        }
        place?.let {
            mapView.overlays.remove(it)
        }

        val polygon = object : Polygon(mapView) {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val geoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                addMarker(geoPoint)
                return true // Consume the event so no info window appears
            }
        }.apply {
            this.points = points
            fillColor = Color.argb(128, 255, 255, 0)
            strokeColor = Color.RED
            strokeWidth = 3f
        }

        val paddedBoundingBox = BoundingBox(
            boundingBox.latNorth + padding, // Expand north
            boundingBox.lonEast + padding,  // Expand east
            boundingBox.latSouth - padding, // Expand south
            boundingBox.lonWest - padding   // Expand west
        ).let {
            BoundingBox(
                max(it.latNorth, it.latSouth), // Ensure North > South
                max(it.lonEast, it.lonWest),   // Ensure East > West
                min(it.latNorth, it.latSouth), // Ensure South < North
                min(it.lonEast, it.lonWest)    // Ensure West < East
            )
        }

        mapView.setScrollableAreaLimitDouble(paddedBoundingBox)

        mapView.overlays.apply {
            add(0, polygon)
        }

        place = polygon

        mapView.controller.apply {
            setCenter(points.first())
            setZoom(18.0)
        }
        InfoWindow.closeAllInfoWindowsOn(mapView)
        mapView.invalidate()
    }

    fun drawPolygon(points: List<List<GeoPoint>>) {
        if (points.isEmpty()) {
            return
        }

        polygons.forEach {
            mapView.overlays.remove(it)
        }

        points.forEach { polygonPoints ->
            Polygon(mapView).apply {
                this.points = polygonPoints
                fillColor = Color.argb(128, 255, 255, 0)
                strokeColor = Color.RED
                strokeWidth = 3f
                setOnClickListener { polygon, mapView, eventPos -> false }
            }.also {
                polygons.add(it)
                mapView.overlayManager.add(it)
            }
        }


        mapView.controller.apply {
            setCenter(points.first().first())
//            setZoom(18.0)
        }
        mapView.invalidate()
    }

    fun drawMultiPolygon(points: List<List<List<GeoPoint>>>) {
        if (points.isEmpty()) {
            return
        }

        currentBorders?.forEach { mapView.overlayManager.remove(it) }

        points.forEach { polygon ->
            polygon.forEach { polygonPoints ->
                Polygon(mapView).apply {
                    this.points = polygonPoints
                    fillColor = Color.argb(128, 255, 255, 0)
                    strokeColor = Color.RED
                    strokeWidth = 3f
                    setOnClickListener { polygon, mapView, eventPos -> false }
                }.also {
                    polygons.add(it)
                    mapView.overlayManager.add(it)
                }
            }
        }
        mapView.controller.apply {
            setCenter(points.first().first().first())
        }
        mapView.invalidate()
    }

    fun drawPolyline(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            return
        }

        polylines.forEach {
            mapView.overlays.remove(it)
        }

        Polyline(mapView).apply {
            color = Color.RED
            width = 8f
            this.setPoints(points)
        }.also {
            polylines.add(it)
            mapView.overlays.add(it)
        }


        mapView.controller.apply {
            setCenter(points.first())
            setZoom(18.0)
        }
        mapView.invalidate()
    }

    fun drawMultiPolyline(points: List<List<GeoPoint>>) {
        if (points.isEmpty()) {
            return
        }
        polylines.forEach { mapView.overlayManager.remove(it) }

        // Draw each polygon separately
        points.map { polylinePoints ->
            Polyline(mapView).apply {
                this.setPoints(polylinePoints)
                color = Color.RED
                width = 5f
                setOnClickListener { polyline, mapView, eventPos -> false }
            }.also {
                polylines.add(it)
                mapView.overlayManager.add(it)
            }
        }

        mapView.invalidate()
    }

    fun drawPoint(point: GeoPoint) {}

    fun drawMultiPoint(points: List<GeoPoint>) {}

    fun drawRoute(points: List<GeoPoint>) {
        if (points.isEmpty()) {
            return
        }

        route?.let { mapView.overlays.remove(it) }

        val polyline = Polyline(mapView).apply {
            color = Color.BLUE
            width = 8f
            this.setPoints(points)
        }

        startMarker = Marker(mapView).apply {
            position = points.first()
            icon = ContextCompat.getDrawable(
                context,
                R.drawable.ic_marker
            ) // Replace with your marker icon
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        endMarker = Marker(mapView).apply {
            position = points.last()
            icon = ContextCompat.getDrawable(
                context,
                R.drawable.ic_destination
            ) // Replace with your marker icon
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        route = polyline
        mapView.overlayManager.apply {
            add(polyline)
            add(startMarker)
            add(endMarker)
        }

        mapView.controller.apply {
            setCenter(points.first())
            setZoom(18.0)
        }
        mapView.invalidate()
    }

    fun drawBorder(polygons: List<List<GeoPoint>>) {
        if (polygons.isEmpty()) {
            return
        }
        currentBorders?.forEach { mapView.overlayManager.remove(it) }

        // Draw each polygon separately
        val newBorders = polygons.map { polygonPoints ->
            Polygon(mapView).apply {
                this.points = polygonPoints
                strokeColor = Color.BLUE
                strokeWidth = 4f
                setOnClickListener { polygon, mapView, eventPos -> false }
            }.also { mapView.overlayManager.add(it) }
        }

        currentBorders = newBorders



        mapView.invalidate()
    }

    fun setOpenTopoMap() {
        val openTopoMap = object : OnlineTileSourceBase(
            "OpenTopoMap", 0, 18, 256, "",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return "$baseUrl${getZoom(pMapTileIndex)}/${getX(pMapTileIndex)}/${
                    getY(
                        pMapTileIndex
                    )
                }.png"
            }
        }
        mapView.setTileSource(openTopoMap)
    }

    fun removePolygon() {
        polygons.forEach {
            mapView.overlays.remove(it)
        }
        place?.let {
            mapView.overlays.remove(it) // Remove polygon from overlays
            place = null // Reset reference
        }
        mapView.invalidate() // Force map refresh
    }

    fun removeRoute() {
        route?.let {
            mapView.overlays.remove(startMarker)
            mapView.overlays.remove(endMarker)
            mapView.overlays.remove(it)
            route = null
        }
        mapView.invalidate()
    }

}
