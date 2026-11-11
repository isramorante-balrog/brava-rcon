package io.github.isramorante.brava.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import io.github.isramorante.brava.data.J
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Release(val build: Int, val url: String, val sizeBytes: Long)

/**
 * Looks for newer builds in the project's GitHub releases and hands the APK to the system installer.
 * Android installs it over the current app, so saved servers and settings stay. The system only
 * accepts an APK signed with the same key as the installed app.
 */
class Updater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val installedBuild: Int
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()

    /** The newest published build, or null when there is none. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASES).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty reply")
            J.parseToJsonElement(body).jsonArray.mapNotNull { element ->
                val release = element.jsonObject
                val build = release["tag_name"]?.jsonPrimitive?.content?.removePrefix("build-")?.toIntOrNull() ?: return@mapNotNull null
                val asset = release["assets"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                    ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!url.startsWith(DOWNLOADS)) return@mapNotNull null
                Release(build, url, asset["size"]?.jsonPrimitive?.long ?: 0L)
            }.maxByOrNull { it.build }
        }
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "brava-rcon-${release.build}.apk")
        http.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            val total = if (body.contentLength() > 0) body.contentLength() else release.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != context.packageName) {
            file.delete()
            throw IOException("The downloaded file is not a Brava build")
        }
        file
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val RELEASES = "https://api.github.com/repos/isramorante-balrog/brava-rcon/releases?per_page=30"
        private const val DOWNLOADS = "https://github.com/isramorante-balrog/brava-rcon/releases/download/"
    }
}
