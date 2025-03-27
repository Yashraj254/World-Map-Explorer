package com.example.worldmapexplorer.data.network.api

import com.skydoves.sandwich.ApiResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WikidataApi {

    @GET("w/api.php")
    suspend fun getWikiDataEntities(
        @Query("action") action: String = "wbgetentities",
        @Query("origin") origin: String = "*",
        @Query("format") format: String = "json",
        @Query("ids") ids: String,
        @Query("props") props: String = "claims|descriptions"
    ):ApiResponse<ResponseBody>

    @GET("wiki/Special:EntityData/{entityId}.json")
    suspend fun getWikiDataEntityLabel(
        @Path("entityId") entityId: String):ApiResponse<ResponseBody>


//    https://www.wikidata.org/wiki/Special:EntityData/${entityId}.json

}