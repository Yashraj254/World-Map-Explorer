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
import com.example.worldmapexplorer.data.network.dto.LatLon
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.data.network.dto.PlaceBordersDto
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.data.network.dto.RouteRequestDto
import com.example.worldmapexplorer.data.network.dto.RouteResponseDto
import com.example.worldmapexplorer.utils.convertToGeoJSON
import com.example.worldmapexplorer.utils.polygonArea

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.suspendMapSuccess
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private lateinit var locationCoordinates: LatLon
    private lateinit var geoJson: GeoJsonGeometry
    private lateinit var displayName: String

    suspend fun fetchPlaces(query: String, excludedPaces: String): ApiResponse<List<Place>> {
        return nominatimClient.fetchPlaces(query, excludedPaces)
    }


    suspend fun getGeometry(
        query: String,
    ): ApiResponse<GeoJsonGeometry> = withContext(Dispatchers.IO) {
        nominatimClient.getGeometry(query).suspendMapSuccess {
            val response = string()
            json = JSONObject(response)
            geoJson = convertToGeoJSON(response) // Convert ResponseBody to GeoJsonGeometry
            geoJson
        }
    }

    suspend fun getPlaceDetailsForWiki(
        osmType: String,
        placeInfo: PlaceInfo,
        osmId: Long
    ): PlaceDetails? {
        displayName = placeInfo.displayName
        val query = "[out:json];$osmType($osmId);out tags center;"
        getPlaceTagsCenter(query)

        val element = json.optJSONArray("elements")?.optJSONObject(0) ?: return null
        val type = element.optString("type", "")
        val id = element.optLong("id", -1)

        return nominatimClient.getPlaceDetailsForWiki(id, type).suspendMapSuccess {
            val response = JSONObject(string())
            val wikiId = response.optJSONObject("extratags")?.optString("wikidata")?:""
            val adminLevel = response.opt("admin_level")?.toString()?.toIntOrNull() ?: -1

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
        val country = CountryDetails.Builder()
        country.name(displayName)

        if (entityId == null) return country.build()

        wikidataClient.getWikidataEntities(ids = entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

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
            country.population(population)

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
                country.area("$area km²")
            }

            val languageClaims = claims?.optJSONArray("P37")
            val languages = mutableListOf<String>()
            languageClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            getWikidataEntityLabel(id).suspendOnSuccess { languages.add(data) }
                        }
                }
            }
            country.language(languages.joinToString(", "))

            country.coordinates("${locationCoordinates.lat},${locationCoordinates.lon}")

            val borderClaims = claims?.optJSONArray("P47")
            val borders = mutableListOf<String>()
            borderClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                            getWikidataEntityLabel(id).suspendOnSuccess {
                                borders.add(data)
                                country.borders(borders.joinToString(", "))
                            }
                        }
                }
            }
        }
        return country.build()
    }

    private suspend fun fetchStateDetails(entityId: String): StateDetails {
        val state = StateDetails.Builder()
        state.name(displayName)
        wikidataClient.getWikidataEntities(ids = entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P17")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                getWikidataEntityLabel(it).suspendOnSuccess { state.country(data) }
            }

            claims?.optJSONArray("P36")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                getWikidataEntityLabel(it).suspendOnSuccess { state.capital(data) }
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
                state.area("$area km²")
            }

            val borderClaims = claims?.optJSONArray("P47")
            val coordinates = locationCoordinates
            state.coordinates("${coordinates.lat}, ${coordinates.lon}")
            val borders = mutableListOf<String>()

            borderClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                        getWikidataEntityLabel(id).suspendOnSuccess {
                            borders.add(data)
                            state.borders(borders.joinToString(", "))
                        }
                    }
                }
            }

            response.optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value")?.let {
                state.summary(it)
            }
        }
        return state.build()
    }

    private suspend fun fetchDistrictDetails(entityId: String): DistrictDetails {
        val district = DistrictDetails.Builder()
        district.name(displayName)
        wikidataClient.getWikidataEntities(entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P131")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                getWikidataEntityLabel(it).suspendOnSuccess { district.state(data) }
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
                district.area("$area km²")
            }

            val borderClaims = claims?.optJSONArray("P47")
            val coordinates = locationCoordinates
            district.coordinates("${coordinates.lat}, ${coordinates.lon}")
            val borders = mutableListOf<String>()

            borderClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                        getWikidataEntityLabel(id).suspendOnSuccess {
                            borders.add(data)
                            district.borders(borders.joinToString(", "))
                        }
                    }
                }
            }

            response.optJSONObject("entities")?.optJSONObject(entityId)
                ?.optJSONObject("descriptions")?.optJSONObject("en")?.optString("value")?.let {
                district.summary(it)
            }
        }
        return district.build()
    }

    private suspend fun fetchRiverDetails(entityId: String): RiverDetails {
        val river = RiverDetails.Builder()
        val tags = json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("tags")
        river.name(tags?.optString("name") ?: "Unknown")
        wikidataClient.getWikidataEntities(entityId).suspendOnSuccess {
            val response = JSONObject(data.string())
            val claims =
                response.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("claims")

            claims?.optJSONArray("P2043")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("amount")
                ?.removePrefix("+")?.let {
                river.length(it)
            }

            claims?.optJSONArray("P885")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                getWikidataEntityLabel(it).suspendOnSuccess { river.origin(data) }
            }

            claims?.optJSONArray("P403")?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")?.let {
                getWikidataEntityLabel(it).suspendOnSuccess { river.mouth(data) }
            }

            val tributariesClaims = claims?.optJSONArray("P974")
            val tributaries = mutableListOf<String>()

            tributariesClaims?.let {
                for (i in 0 until it.length()) {
                    it.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")
                        ?.optJSONObject("value")?.optString("id")?.let { id ->
                        getWikidataEntityLabel(id).suspendOnSuccess {
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
        val otherArea = OtherAreaDetails.Builder()
        otherArea.name(name)
        otherArea.address(address)
        val tagsJson =
            json.optJSONArray("elements")?.optJSONObject(0)?.optJSONObject("tags") ?: JSONObject()
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
            otherArea.area("$area km²")
        }
        val keys = tagsJson.keys() // Gets an Iterator<String> of keys
        while (keys.hasNext()) {
            val key = keys.next()
            tags[key] = tagsJson.optString(key, "") // Extracts value as String safely
        }

        val prefix = fetchPrefix(tags)
        otherArea.type(prefix)

        return otherArea.build()
    }

    private suspend fun getPlaceTagsCenter(query: String) {
        nominatimClient.getPlaceTagsCenter(query).suspendMapSuccess {
            val json = JSONObject(string())
            val element = json.optJSONArray("elements")?.optJSONObject(0)
            val centerObj = element?.optJSONObject("center")
            val lat = centerObj?.optDouble("lat", 0.0) ?: 0.0
            val lon = centerObj?.optDouble("lon", 0.0) ?: 0.0
            locationCoordinates = LatLon(lat, lon)
        }
    }

    suspend fun fetchPrefix(result: Map<String, String>): String {
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

        return prefix
    }

    suspend fun getRoute(locations: List<LatLon>): ApiResponse<RouteResponseDto> {
        val routeRequestDto =
            RouteRequestDto(locations, "auto", RouteRequestDto.DirectionsOptions("km"))
        return routeClient.getRoute(routeRequestDto)
    }

    suspend fun getElevation(lat: Double, lon: Double) = elevationClient.getElevation(lat, lon)

    suspend fun getPlacesBorder(
        lat: Double,
        lon: Double,
        zoom: Int,
    ): ApiResponse<PlaceBordersDto> {
        return nominatimClient.getPlaceBorders(
            lat,
            lon,
            getFixedZoom(zoom),
            getThreshold(zoom.toDouble())
        )
    }

    suspend fun getPlaceDetails(lat: Double, lon: Double, zoom: Int) =
        nominatimClient.getPlaceDetails(
            lat,
            lon,
            getFixedZoom(zoom)
        )

    suspend fun getWikidataEntityLabel(entityId: String): ApiResponse<String> =
        withContext(Dispatchers.IO) {
            wikidataClient.getWikidataEntityLabel(entityId).suspendMapSuccess {
                val response = JSONObject(string())
                val label = response.optJSONObject("entities")?.optJSONObject(entityId)
                    ?.optJSONObject("labels")
                    ?.optJSONObject("en")?.optString("value") ?: "Unkown"
                label
            }
        }


    private fun getFixedZoom(zoom: Int): Int {
        return if (zoom >= 8) {
            6
        } else if (zoom in 5..7) {
            5
        } else {
            2
        }
    }

    private fun getThreshold(zoom: Double): Double {
        return 1 / zoom.pow(3)
    }

}