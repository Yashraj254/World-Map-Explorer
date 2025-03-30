package com.example.worldmapexplorer.data.repository

import android.content.Context
import com.example.worldmapexplorer.data.models.CountryDetails
import com.example.worldmapexplorer.data.models.DistrictDetails
import com.example.worldmapexplorer.data.models.OtherAreaDetails
import com.example.worldmapexplorer.data.models.PlaceDetails
import com.example.worldmapexplorer.data.models.RiverDetails
import com.example.worldmapexplorer.data.models.StateDetails
import com.example.worldmapexplorer.data.network.client.ElevationClient
import com.example.worldmapexplorer.data.network.client.NominatimClient
import com.example.worldmapexplorer.data.network.client.RouteClient
import com.example.worldmapexplorer.data.network.client.WikidataClient
import com.example.worldmapexplorer.data.network.dto.ElevationResponseDto
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.network.dto.RouteRequestDto
import com.example.worldmapexplorer.data.network.dto.RouteResponseDto
import com.example.worldmapexplorer.utils.convertToGeoJSON
import com.example.worldmapexplorer.utils.polygonArea
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrElse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.suspendMapSuccess
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.pow

class PlacesRepository @Inject constructor(
    private val nominatimClient: NominatimClient,
    private val routeClient: RouteClient,
    private val elevationClient: ElevationClient,
    private val wikidataClient: WikidataClient,
    @ApplicationContext private val context: Context
) {

    private lateinit var json: JSONObject
    private lateinit var locationCoordinates: GeoPoint
    private lateinit var geoJson: GeoJsonGeometry
    private lateinit var displayName: String

    suspend fun fetchPlaces(query: String, excludedPaces: String): ApiResponse<List<Place>> {
        return nominatimClient.fetchPlaces(query, excludedPaces)
    }

    suspend fun getGeometry(
        query: String,
        osmType: String,
        osmId: Long
    ): ApiResponse<GeoJsonGeometry> = withContext(Dispatchers.IO) {
//        Timber.d("Fetching geometry for query: $query")
        nominatimClient.getGeometry(query).suspendMapSuccess {
            val response = string()
//            Timber.d("Received response: $response")
            json = JSONObject(response)

            val element = json.getJSONArray("elements").getJSONObject(0)
            val type = element.optString("type")
            val id = element.optLong("id")
            Timber.d("Extracted element type: $type, id: $id")

            // Fetch place details
            val placeDetailsResponse = nominatimClient.getPlaceDetailsForWiki(id, type).suspendMapSuccess {
                JSONObject(string())
            }.getOrNull() // Ensure it doesn't crash

            val adminLevel = placeDetailsResponse?.opt("admin_level")?.toString()?.toIntOrNull() ?: -1

            if (adminLevel <= 5) {
                locationCoordinates = getPlaceTagsCenter(osmType, osmId)
            } else {
                val boundsJson = element.optJSONObject("bounds")
                boundsJson?.let {
                    val bbox = arrayOf(
                        it.optDouble("minlat", 0.0),
                        it.optDouble("minlon", 0.0),
                        it.optDouble("maxlat", 0.0),
                        it.optDouble("maxlon", 0.0)
                    )
                    val finalCenterLatitude = (bbox[0] + bbox[2]) / 2
                    val finalCenterLongitude = (bbox[1] + bbox[3]) / 2
                    Timber.d("Calculated final center coordinates: lat=$finalCenterLatitude, lon=$finalCenterLongitude")
                    locationCoordinates = GeoPoint(finalCenterLatitude, finalCenterLongitude)
                }
            }
            geoJson = convertToGeoJSON(response,locationCoordinates)
//            Timber.d("Converted response to GeoJSON: $geoJson")
            geoJson
        }
    }

    suspend fun getPlaceDetailsForWiki(
        osmType: String,
        placeInfo: PlaceInfo,
        osmId: Long
    ): PlaceDetails? {
        Timber.d("Fetching place details for osmType: $osmType, osmId: $osmId")
        displayName = placeInfo.displayName

        val element = json.optJSONArray("elements")?.optJSONObject(0) ?: return null
        val type = element.optString("type")
        val id = element.optLong("id")
        Timber.d("Extracted element type: $type, id: $id")

        return nominatimClient.getPlaceDetailsForWiki(id, type).suspendMapSuccess {
            val response = JSONObject(string())
//            Timber.d("Received place details: $response")
            val wikiId = response.optJSONObject("extratags")?.optString("wikidata") ?: ""
            val adminLevel = response.opt("admin_level")?.toString()?.toIntOrNull() ?: -1

            Timber.d("Admin level: $adminLevel, Wikidata ID: $wikiId")

            when {
                adminLevel <= 2 -> fetchCountryDetails(wikiId)
                adminLevel in 3..4 -> fetchStateDetails(wikiId)
                adminLevel == 5 -> fetchDistrictDetails(wikiId)
                response.optString("type") == "waterway" || response.optString("category") == "waterway" -> fetchRiverDetails(
                    wikiId
                )

                else -> fetchOtherAreaDetails(placeInfo.name, placeInfo.displayName)
            }
        }.getOrNull()
    }

    private suspend fun fetchCountryDetails(entityId: String?): CountryDetails {
        Timber.d("Fetching country details for entityId: $entityId")
        val country = CountryDetails.Builder()
        country.name(displayName)

        if (entityId == null) return country.build()

        wikidataClient.getWikidataEntities(ids = entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
//            Timber.d("Wikidata response: $response")

            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")
            Timber.d("Extracted claims: $claims")

            val continentClaim = claims?.optJSONArray("P30")?.optJSONObject(0)
            continentClaim?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                ?.optJSONObject("value")?.optString("id")?.let {
                    getWikidataEntityLabel(it).suspendOnSuccess { country.continent(data) }
                }

            val capitalClaim = claims?.optJSONArray("P36")?.optJSONObject(0)
            capitalClaim?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                ?.optJSONObject("value")?.optString("id")?.let {
                    getWikidataEntityLabel(it).suspendOnSuccess { country.capital(data) }
                }

            val population = claims?.optJSONArray("P1082")?.optJSONObject(0)
                ?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                ?.optJSONObject("value")?.optString("amount")?.removePrefix("+") ?: "Unknown"
            Timber.d("Extracted population: $population")
            country.population(population)

            if (geoJson.type == "Polygon" || geoJson.type == "MultiPolygon") {
                val area = when (geoJson) {
                    is GeoJsonGeometry.Polygon -> polygonArea((geoJson as GeoJsonGeometry.Polygon).coordinates)
                    is GeoJsonGeometry.MultiPolygon -> (geoJson as GeoJsonGeometry.MultiPolygon).coordinates.sumOf {
                        polygonArea(it)
                    }

                    else -> 0.0
                }
                Timber.d("Calculated area: $area km²")
                country.area("$area km²")
            }
        }
        return country.build()
    }

    private suspend fun fetchStateDetails(entityId: String): StateDetails {
        Timber.d("Fetching state details for entity: $entityId")
        val state = StateDetails.Builder()
        state.name(displayName)

        wikidataClient.getWikidataEntities(ids = entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
//            Timber.d("Received response for state: $response")

            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P17")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                    getWikidataEntityLabel(it).suspendOnSuccess {
                        Timber.d("Fetched country: $data")
                        state.country(data)
                    }
                }

            claims?.optJSONArray("P36")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                    getWikidataEntityLabel(it).suspendOnSuccess {
                        Timber.d("Fetched capital: $data")
                        state.capital(data)
                    }
                }

            if (geoJson.type == "Polygon" || geoJson.type == "MultiPolygon") {
                val area = when (geoJson) {
                    is GeoJsonGeometry.Polygon -> polygonArea((geoJson as GeoJsonGeometry.Polygon).coordinates)
                    is GeoJsonGeometry.MultiPolygon -> (geoJson as GeoJsonGeometry.MultiPolygon).coordinates.sumOf {
                        polygonArea(
                            it
                        )
                    }

                    else -> 0.0
                }
                Timber.d("Calculated area: $area km²")
                state.area("$area km²")
            }

            val borderClaims = claims?.optJSONArray("P47")
            val coordinates = locationCoordinates
            Timber.d("State coordinates: ${coordinates.latitude}, ${coordinates.longitude}")
            state.coordinates("${coordinates.latitude}, ${coordinates.longitude}")

            val borders = mutableListOf<String>()
            borderClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            getWikidataEntityLabel(id).suspendOnSuccess {
                                borders.add(data)
                                Timber.d("Added border: $data")
                                state.borders(borders.joinToString(", "))
                            }
                        }
                }
            }

            response.optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value")?.let {
                    Timber.d("Fetched summary: $it")
                    state.summary(it)
                }
        }
        return state.build()
    }

    private suspend fun fetchDistrictDetails(entityId: String): DistrictDetails {
        Timber.d("Fetching district details for entityId: $entityId")
        val district = DistrictDetails.Builder()
        district.name(displayName)

        wikidataClient.getWikidataEntities(entityId).suspendOnSuccess {
            Timber.d("Wikidata API response received for entityId: $entityId")
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P131")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                    Timber.d("State entity ID found: $it")
                    getWikidataEntityLabel(it).suspendOnSuccess {
                        Timber.d("State name retrieved: $data")
                        district.state(data)
                    }
                }

            if (geoJson.type == "Polygon" || geoJson.type == "MultiPolygon") {
                val area = when (geoJson) {
                    is GeoJsonGeometry.Polygon -> polygonArea((geoJson as GeoJsonGeometry.Polygon).coordinates)
                    is GeoJsonGeometry.MultiPolygon -> (geoJson as GeoJsonGeometry.MultiPolygon).coordinates.sumOf {
                        polygonArea(it)
                    }

                    else -> 0.0
                }
                Timber.d("Calculated area: $area km²")
                district.area("$area km²")
            }

            val borderClaims = claims?.optJSONArray("P47")
            val coordinates = locationCoordinates
            Timber.d("Coordinates: ${coordinates.latitude}, ${coordinates.longitude}")
            district.coordinates("${coordinates.latitude}, ${coordinates.longitude}")

            val borders = mutableListOf<String>()
            borderClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            Timber.d("Fetching border label for entityId: $id")
                            getWikidataEntityLabel(id).suspendOnSuccess {
                                Timber.d("Border retrieved: $data")
                                borders.add(data)
                                district.borders(borders.joinToString(", "))
                            }
                        }
                }
            }

            response.optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value")?.let {
                    Timber.d("District summary retrieved: $it")
                    district.summary(it)
                }
        }
        return district.build()
    }

    private suspend fun fetchRiverDetails(entityId: String): RiverDetails {
        Timber.d("Fetching river details for entityId: $entityId")
        val river = RiverDetails.Builder()
        val tags = json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("tags")
        val boundsJson = json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("bounds")
        boundsJson?.let {
            val bbox = arrayOf(boundsJson.getDouble("minlat"), boundsJson.getDouble("minlon"), boundsJson.getDouble("maxlat"), boundsJson.getDouble("maxlon"))
            val finalCenterLatitude = (bbox[0] + bbox[2]) / 2
            val finalCenterLongitude = (bbox[1] + bbox[3]) / 2
            Timber.d("Calculated final center coordinates: lat=$finalCenterLatitude, lon=$finalCenterLongitude")
            river.coordinates("$finalCenterLatitude, $finalCenterLongitude")
        }
        val riverName = tags?.optString("name") ?: "Unknown"
        Timber.d("River name: $riverName")
        river.name(riverName)

        wikidataClient.getWikidataEntities(entityId).suspendOnSuccess {
            Timber.d("Wikidata API response received for entityId: $entityId")
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P2043")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("amount")
                ?.removePrefix("+")?.let {
                    Timber.d("River length: $it km")
                    river.length(it)
                }

            claims?.optJSONArray("P885")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                    Timber.d("Fetching river origin label for entityId: $it")
                    getWikidataEntityLabel(it).suspendOnSuccess {
                        Timber.d("River origin: $data")
                        river.origin(data)
                    }
                }

            claims?.optJSONArray("P403")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                    Timber.d("Fetching river mouth label for entityId: $it")
                    getWikidataEntityLabel(it).suspendOnSuccess {
                        Timber.d("River mouth: $data")
                        river.mouth(data)
                    }
                }

            val tributariesClaims = claims?.optJSONArray("P974")
            val tributaries = mutableListOf<String>()
            tributariesClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            Timber.d("Fetching tributary label for entityId: $id")
                            getWikidataEntityLabel(id).suspendOnSuccess {
                                Timber.d("Tributary retrieved: $data")
                                tributaries.add(data)
                                river.tributaries(tributaries.joinToString(", "))
                            }
                        }
                }
            }
        }
        return river.build()
    }

    private suspend fun fetchOtherAreaDetails(name: String, address: String): OtherAreaDetails {
        Timber.d("Fetching OtherAreaDetails for name: $name, address: $address")
        val otherArea = OtherAreaDetails.Builder()
        otherArea.name(name)
        otherArea.address(address)
        val tagsJson =
            json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("tags") ?: JSONObject()
        val boundsJson = json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("bounds")
        boundsJson?.let {
            val bbox = arrayOf(boundsJson.getDouble("minlat"), boundsJson.getDouble("minlon"), boundsJson.getDouble("maxlat"), boundsJson.getDouble("maxlon"))
            val finalCenterLatitude = (bbox[0] + bbox[2]) / 2
            val finalCenterLongitude = (bbox[1] + bbox[3]) / 2
            Timber.d("Calculated final center coordinates: lat=$finalCenterLatitude, lon=$finalCenterLongitude")
            otherArea.coordinates("$finalCenterLatitude, $finalCenterLongitude")
        }
        val tags = mutableMapOf<String, String>()

        if (geoJson.type == "Polygon" || geoJson.type == "MultiPolygon") {
            val area = when (geoJson) {
                is GeoJsonGeometry.Polygon -> {
                    polygonArea((geoJson as GeoJsonGeometry.Polygon).coordinates)
                }

                is GeoJsonGeometry.MultiPolygon -> {
                    var area = 0.0
                    (geoJson as GeoJsonGeometry.MultiPolygon).coordinates.forEach {
                        area += polygonArea(it)
                    }
                    area
                }

                else -> 0.0 // Handle unexpected cases
            }
            Timber.d("Computed area: $area km² for type: ${geoJson.type}")
            otherArea.area("$area km²")
        }

        val keys = tagsJson.keys() // Gets an Iterator<String> of keys
        while (keys.hasNext()) {
            val key = keys.next()
            tags[key] = tagsJson.optString(key, "") // Extracts value as String safely
        }
        Timber.d("Extracted tags: $tags")

        val prefix = fetchPrefix(tags)
        Timber.d("Determined prefix: $prefix")
        otherArea.type(prefix)

        return otherArea.build()
    }



    private suspend fun getPlaceTagsCenter(osmType: String, osmId: Long): GeoPoint {
        val query = "[out:json];$osmType($osmId);out tags center;"

        return nominatimClient.getPlaceTagsCenter(query).suspendMapSuccess {
            val json = JSONObject(string())
            val element = json.optJSONArray("elements")?.optJSONObject(0)
            val centerObj = element?.optJSONObject("center")
            val lat = centerObj?.optDouble("lat", 0.0) ?: 0.0
            val lon = centerObj?.optDouble("lon", 0.0) ?: 0.0
           Timber.d("Extracted coordinates: lat=$lat, lon=$lon")

           GeoPoint(lat, lon)
        }.getOrElse(GeoPoint(0.0,0.0))
    }

    suspend fun fetchPrefix(result: Map<String, String>): String {
        Timber.d("Fetching prefix for tags: $result")
        val jsonString = context.assets.open("prefix.json").bufferedReader().use { it.readText() }
        val data = JSONObject(jsonString)
        val prefixes = data.optJSONObject("prefix") ?: JSONObject()

        var prefix = ""

        if (result["boundary"] == "administrative" && result.containsKey("admin_level")) {
            val adminLevel = "level" + result["admin_level"]
            prefix = prefixes.optJSONObject("admin_levels")?.optString(adminLevel, "") ?: ""
        } else {
            for ((key, value) in result) {
                if (prefixes.has(key)) {
                    val keyObject = prefixes.optJSONObject(key)
                    if (keyObject?.has(value) == true) {
                        return keyObject.optString(value, "")
                    }
                }
            }

            for ((key, value) in result) {
                if (prefixes.has(key)) {
                    val formattedValue =
                        value.replaceFirstChar { it.uppercaseChar() }.replace("_", " ")
                    return formattedValue
                }
            }
        }

        Timber.d("Final determined prefix: $prefix")
        return prefix
    }

    suspend fun getRoute(locations: List<LatLon>): ApiResponse<RouteResponseDto> {
        Timber.d("Fetching route for locations: $locations")
        val routeRequestDto =
            RouteRequestDto(locations, "auto", RouteRequestDto.DirectionsOptions("km"))
        return routeClient.getRoute(routeRequestDto).also {
            Timber.d("Route response received: $it")
        }
    }

    suspend fun getElevation(lat: Double, lon: Double): ApiResponse<ElevationResponseDto> {
        Timber.d("Fetching elevation for lat: $lat, lon: $lon")
        return elevationClient.getElevation(lat, lon).also {
            Timber.d("Elevation response: $it")
        }
    }

    suspend fun getPlacesBorder(
        lat: Double,
        lon: Double,
        zoom: Int,
    ): ApiResponse<PlaceBordersDto> {
        val fixedZoom = getFixedZoom(zoom)
        val threshold = getThreshold(zoom.toDouble())
        Timber.d("Fetching place borders for lat: $lat, lon: $lon, zoom: $zoom (fixedZoom: $fixedZoom, threshold: $threshold)")
        return nominatimClient.getPlaceBorders(lat, lon, fixedZoom, threshold).also {
            Timber.d("Place borders response: $it")
        }
    }

    suspend fun getPlaceDetails(lat: Double, lon: Double, zoom: Int) =
        nominatimClient.getPlaceDetails(
            lat,
            lon,
            getFixedZoom(zoom)
        )

    suspend fun getWikidataEntityLabel(entityId: String): ApiResponse<String> =
        withContext(Dispatchers.IO) {
            Timber.d("Fetching Wikidata label for entityId: $entityId")
            wikidataClient.getWikidataEntityLabel(entityId).suspendMapSuccess {
                val response = JSONObject(string())
                val label = response.optJSONObject("entities")?.optJSONObject(entityId)
                    ?.optJSONObject("labels")
                    ?.optJSONObject("en")?.optString("value") ?: "Unknown"
                Timber.d("Wikidata label received: $label")
                label
            }
        }

    private fun getFixedZoom(zoom: Int): Int {
        val fixedZoom = when {
            zoom >= 8 -> 6
            zoom in 5..7 -> 5
            else -> 2
        }
        Timber.d("Calculated fixed zoom: $fixedZoom for input zoom: $zoom")
        return fixedZoom
    }

    private fun getThreshold(zoom: Double): Double {
        val threshold = 1 / zoom.pow(3)
        Timber.d("Calculated threshold: $threshold for input zoom: $zoom")
        return threshold
    }


}