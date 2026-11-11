package io.github.isramorante.brava.panel

import io.github.isramorante.brava.data.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PanelStatus(
    val state: String,
    val memoryBytes: Long,
    val memoryLimitBytes: Long,
    val cpuPercent: Double,
    val diskBytes: Long,
    val diskLimitBytes: Long,
)

/** Minimal client for the Pterodactyl client API used by most game hosts. */
class PanelApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun base(p: ServerProfile): String {
        var url = p.panelUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return "$url/api/client/servers/${p.panelServerId.trim()}"
    }

    private fun builder(p: ServerProfile, path: String): Request.Builder = Request.Builder()
        .url(base(p) + path)
        .header("Authorization", "Bearer ${p.panelKey.trim()}")
        .header("Accept", "application/json")

    private fun getJson(p: ServerProfile, path: String): JsonObject {
        http.newCall(builder(p, path).build()).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw IllegalStateException("Panel answered HTTP ${res.code}")
            return Json.parseToJsonElement(body).jsonObject
        }
    }

    suspend fun status(p: ServerProfile): PanelStatus = withContext(Dispatchers.IO) {
        val limits = getJson(p, "")["attributes"]!!.jsonObject["limits"]!!.jsonObject
        val attrs = getJson(p, "/resources")["attributes"]!!.jsonObject
        val res = attrs["resources"]!!.jsonObject
        PanelStatus(
            state = attrs["current_state"]?.jsonPrimitive?.content ?: "unknown",
            memoryBytes = res["memory_bytes"]?.jsonPrimitive?.longOrNull ?: 0,
            memoryLimitBytes = (limits["memory"]?.jsonPrimitive?.longOrNull ?: 0) * 1024 * 1024,
            cpuPercent = res["cpu_absolute"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            diskBytes = res["disk_bytes"]?.jsonPrimitive?.longOrNull ?: 0,
            diskLimitBytes = (limits["disk"]?.jsonPrimitive?.longOrNull ?: 0) * 1024 * 1024,
        )
    }

    suspend fun power(p: ServerProfile, signal: String) = withContext(Dispatchers.IO) {
        val body = "{\"signal\":\"$signal\"}".toRequestBody("application/json".toMediaType())
        val req = builder(p, "/power").header("Content-Type", "application/json").post(body).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Panel answered HTTP ${res.code}")
        }
    }
}
