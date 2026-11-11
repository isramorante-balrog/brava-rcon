package io.github.isramorante.brava.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.isramorante.brava.MainActivity
import io.github.isramorante.brava.R
import io.github.isramorante.brava.data.ServerProfile
import io.github.isramorante.brava.data.Store
import io.github.isramorante.brava.tr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Checks the watched servers every 15 minutes (the shortest period Android allows) and
 * notifies when one stops answering RCON, and again when it comes back. A server must fail
 * two checks in a row before the alert, so a normal restart does not set it off.
 */
class ServerWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val watched = store.loadWatched()
        val servers = store.loadServers().filter { it.id in watched }
        if (servers.isEmpty()) return Result.success()
        val state = applicationContext.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

        for (server in servers) {
            var up = probe(server)
            if (!up) {
                delay(120_000)
                up = probe(server)
            }
            val previous = state.getString(server.id, "up") ?: "up"
            val next = when {
                up -> "up"
                previous == "up" -> "suspect"
                else -> "down"
            }
            if (previous == "down" && next == "up") {
                notify(server, tr("${server.name} is back", "${server.name} volvió"), tr("The server answers RCON again.", "El servidor ya responde al RCON."))
            }
            if (previous == "suspect" && next == "down") {
                notify(
                    server,
                    tr("${server.name} is down", "${server.name} está caído"),
                    tr(
                        "No RCON answer for about 15 minutes. Open Brava to restart it from the hosting panel.",
                        "Sin respuesta del RCON desde hace unos 15 minutos. Abre Brava para reiniciarlo desde el panel.",
                    ),
                )
            }
            state.edit().putString(server.id, next).apply()
        }
        return Result.success()
    }

    /** True when the server opens WebRCON and answers one command within 15 seconds. */
    private suspend fun probe(server: ServerProfile): Boolean {
        val url = try {
            HttpUrl.Builder().scheme("http").host(server.host.trim()).port(server.port).addPathSegment(server.password).build()
        } catch (e: Exception) {
            return false
        }
        val answer = CompletableDeferred<Boolean>()
        val socket = http.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("{\"Identifier\":7,\"Message\":\"serverinfo\",\"Name\":\"BravaWatch\"}")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"Identifier\": 7") || text.contains("\"Identifier\":7")) answer.complete(true)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    answer.complete(false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    answer.complete(false)
                }
            },
        )
        val result = withTimeoutOrNull(15_000) { answer.await() } ?: false
        socket.close(1000, null)
        return result
    }

    private fun notify(server: ServerProfile, title: String, text: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brava)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(server.id.hashCode(), notification)
    }

    companion object {
        private const val WORK = "brava-server-watch"
        private const val CHANNEL = "server_status"
        private const val STATE_FILE = "brava_watch_state"

        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Server status", "Estado del servidor"), NotificationManager.IMPORTANCE_HIGH),
            )
        }

        /** Starts or stops the periodic check depending on whether any server is watched. */
        fun schedule(context: Context, anyWatched: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!anyWatched) {
                work.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<ServerWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
