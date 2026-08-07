package com.momory.app.location

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyPlace(val name: String, val distanceMeters: Int, val address: String?)

@Serializable
private data class OverpassResponse(val elements: List<OverpassElement> = emptyList())

@Serializable
private data class OverpassElement(
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String>? = null
)

@Serializable
private data class OverpassCenter(val lat: Double, val lon: Double)

/**
 * Recherche de lieux réels à proximité via Overpass (OpenStreetMap) — gratuit, sans clé API,
 * cohérent avec la philosophie "local/self-hosted" du reste de l'appli. Permet au modèle Ollama
 * de répondre avec de vrais lieux plutôt que d'en inventer (il n'a lui-même aucun accès internet).
 */
class PlacesClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun searchNearby(lat: Double, lon: Double, tagKey: String, tagValue: String, radiusMeters: Int = 2000): List<NearbyPlace> {
        val overpassQuery = """
            [out:json][timeout:15];
            (
              node["$tagKey"="$tagValue"](around:$radiusMeters,$lat,$lon);
              way["$tagKey"="$tagValue"](around:$radiusMeters,$lat,$lon);
            );
            out center 20;
        """.trimIndent()

        val body = ("data=" + URLEncoder.encode(overpassQuery, "UTF-8"))
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val bodyStr = resp.body?.string() ?: return emptyList()
            val parsed = json.decodeFromString<OverpassResponse>(bodyStr)
            return parsed.elements
                .mapNotNull { el ->
                    val name = el.tags?.get("name") ?: return@mapNotNull null
                    val placeLat = el.lat ?: el.center?.lat ?: return@mapNotNull null
                    val placeLon = el.lon ?: el.center?.lon ?: return@mapNotNull null
                    val distance = haversineMeters(lat, lon, placeLat, placeLon)
                    val street = el.tags["addr:street"]
                    val number = el.tags["addr:housenumber"]
                    val address = if (street != null) listOfNotNull(number, street).joinToString(" ") else null
                    NearbyPlace(name, distance.toInt(), address)
                }
                .sortedBy { it.distanceMeters }
                .take(8)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
