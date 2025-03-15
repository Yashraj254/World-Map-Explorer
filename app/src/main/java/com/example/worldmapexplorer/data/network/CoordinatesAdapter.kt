package com.example.worldmapexplorer.data.network

import com.squareup.moshi.*
import java.lang.reflect.Type

class CoordinatesAdapter : JsonAdapter<List<Any>>() {
    @FromJson
    override fun fromJson(reader: JsonReader): List<Any>? {
        return parseCoordinates(reader)
    }

    private fun parseCoordinates(reader: JsonReader): List<Any>? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                val list = mutableListOf<Any>()
                while (reader.hasNext()) {
                    list.add(parseCoordinates(reader) ?: emptyList<Double>())
                }
                reader.endArray()
                list
            }
            JsonReader.Token.NUMBER -> listOf(reader.nextDouble())
            else -> null
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: List<Any>?) {
        throw UnsupportedOperationException("Serialization not supported")
    }
}
