package com.example.worldmapexplorer.data.network.client

import com.example.worldmapexplorer.data.network.api.WikidataApi
import javax.inject.Inject

class WikidataClient @Inject constructor(private val wikidataApi: WikidataApi) {

    suspend fun getWikidataEntities(ids: String) = wikidataApi.getWikiDataEntities(ids = ids)

    suspend fun getWikidataEntityLabel(entityId: String) = wikidataApi.getWikiDataEntityLabel(entityId = entityId)

}