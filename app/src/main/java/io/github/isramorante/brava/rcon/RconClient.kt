package io.github.isramorante.brava.rcon

import io.github.isramorante.brava.data.ServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class ConnState { Disconnected, Connecting, Connected }

data class RconMessage(val id: Int, val text: String, val type: String)

/**
 * Client for Rust's WebRCON: a WebSocket at ws://host:port/password that exchanges
 * JSON frames {Identifier, Message, Name} and {Identifier, Message, Type}.
 */
class RconClient {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _messages = MutableSharedFlow<RconMessage>(extraBufferCapacity = 512)
    val messages: SharedFlow<RconMessage> = _messages

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val nextId = AtomicInteger(1000)
    private val generation = AtomicInteger(0)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var target: ServerProfile? = null

    fun connect(profile: ServerProfile) {
        disconnect()
        target = profile
        open(profile, generation.incrementAndGet())
    }

    fun disconnect() {
        target = null
        generation.incrementAndGet()
        socket?.close(1000, null)
        socket = null
        failPending("Disconnected")
        _state.value = ConnState.Disconnected
    }

    private fun open(profile: ServerProfile, gen: Int) {
        _state.value = ConnState.Connecting
        val url = try {
            HttpUrl.Builder().scheme("http").host(profile.host.trim()).port(profile.port)
                .addPathSegment(profile.password).build()
        } catch (e: Exception) {
            _error.value = "Invalid address"
            _state.value = ConnState.Disconnected
            return
        }
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener(gen))
    }

    private fun retryLater(gen: Int) {
        scope.launch {
            delay(5000)
            val profile = target
            if (profile != null && generation.get() == gen) open(profile, gen)
        }
    }

    private fun failPending(reason: String) {
        val waiting = pending.values.toList()
        pending.clear()
        waiting.forEach { it.completeExceptionally(IllegalStateException(reason)) }
    }

    /** Sends a command and waits for the reply that carries the same identifier. */
    suspend fun request(command: String, timeoutMs: Long = 8000): String {
        val ws = socket
        if (ws == null || _state.value != ConnState.Connected) throw IllegalStateException("Not connected")
        val id = nextId.incrementAndGet()
        val reply = CompletableDeferred<String>()
        pending[id] = reply
        val frame = buildJsonObject {
            put("Identifier", id)
            put("Message", command)
            put("Name", "Brava")
        }
        if (!ws.send(frame.toString())) {
            pending.remove(id)
            throw IllegalStateException("Send failed")
        }
        return try {
            withTimeout(timeoutMs) { reply.await() }
        } finally {
            pending.remove(id)
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        private fun current() = generation.get() == gen

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current()) return
            _error.value = null
            _state.value = ConnState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            try {
                val obj = Json.parseToJsonElement(text).jsonObject
                val id = obj["Identifier"]?.jsonPrimitive?.intOrNull ?: 0
                val message = obj["Message"]?.jsonPrimitive?.content ?: ""
                val type = obj["Type"]?.jsonPrimitive?.content ?: "Generic"
                val waiter = pending.remove(id)
                if (waiter != null) waiter.complete(message) else _messages.tryEmit(RconMessage(id, message, type))
            } catch (e: Exception) {
                _messages.tryEmit(RconMessage(0, text, "Generic"))
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            failPending("Connection closed")
            _state.value = ConnState.Connecting
            retryLater(gen)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current()) return
            failPending("Connection lost")
            _error.value = t.message ?: t.javaClass.simpleName
            _state.value = ConnState.Connecting
            retryLater(gen)
        }
    }
}
