package com.par.chat.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.min

enum class Conn { CONNECTING, ONLINE, OFFLINE }

data class UploadItem(val id: String, val name: String, val progress: Float, val failed: Boolean = false)

data class ChatState(
    val conn: Conn = Conn.CONNECTING,
    val users: Map<String, UserInfo> = emptyMap(),
    val groups: List<GroupInfo> = emptyList(),
    val msgs: Map<String, List<Msg>> = emptyMap(),
    val unread: Map<String, Int> = emptyMap(),
    /** conversation -> (user id -> expiry time in ms) */
    val typing: Map<String, Map<String, Long>> = emptyMap(),
    val uploads: List<UploadItem> = emptyList(),
    val active: String? = null,
)

@kotlinx.serialization.Serializable
data class HostIce(val stun: String = "", val turn_url: String = "", val turn_user: String = "", val turn_pass: String = "")

sealed class EngineEvent {
    /** A new message from someone else arrived live (used for notifications). */
    data class Incoming(val msg: Msg, val key: String) : EngineEvent()
    /** The host removed or banned this account. */
    data object Kicked : EngineEvent()
    data class Error(val code: String) : EngineEvent()
    data class Call(val data: JsonObject) : EngineEvent()
}

/**
 * Talks to a SHABAKEH PAR host: WebSocket for live events, HTTP for history, sync, search and files.
 * Reconnects with back-off and catches up on missed messages by sequence number.
 */
class ChatEngine(private val app: Application, private val store: Store) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events

    @Volatile var session: Session? = null
        private set
    private var ws: WebSocket? = null
    private var running = false
    private var retry = 0
    private var reconnectJob: Job? = null
    private var consumer: Job? = null
    private var incoming = Channel<String>(Channel.UNLIMITED)
    private val loaded = mutableSetOf<String>()
    private val images = LruCache<String, Bitmap>(40)

    /** STUN/TURN servers shared by the host with its members. */
    @Volatile var hostIce: HostIce? = null
        private set

    val me: String get() = session?.userId ?: ""

    // ---- lifecycle -------------------------------------------------------------

    fun start(s: Session) {
        stop()
        session = s
        running = true
        retry = 0
        loaded.clear()
        _state.value = ChatState(conn = Conn.CONNECTING)
        incoming = Channel(Channel.UNLIMITED)
        val ch = incoming
        consumer = scope.launch { for (t in ch) handle(t) }
        open()
    }

    fun stop() {
        running = false
        reconnectJob?.cancel()
        consumer?.cancel()
        ws?.close(1000, null)
        ws = null
        session = null
        _state.value = ChatState()
    }

    private fun open() {
        val s = session ?: return
        if (!running) return
        val req = try {
            Request.Builder().url("${wsUrl(s.server)}/api/v1/ws").build()
        } catch (e: IllegalArgumentException) {
            return
        }
        _state.update { it.copy(conn = if (it.conn == Conn.ONLINE) Conn.ONLINE else Conn.CONNECTING) }
        lateinit var socket: WebSocket
        socket = Net.wsClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildJsonObject { put("type", "auth"); put("token", s.token) }.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                incoming.trySend(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(socket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(socket)
        })
        ws = socket
    }

    private fun lost(socket: WebSocket) {
        if (socket !== ws || !running) return
        _state.update { it.copy(conn = Conn.OFFLINE) }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(min(15L, 1L shl min(retry, 4)) * 1000)
            retry++
            if (running) open()
        }
    }

    private fun raw(obj: JsonObject): Boolean = ws?.send(obj.toString()) ?: false

    // ---- incoming events -------------------------------------------------------------

    private suspend fun handle(text: String) {
        val o = try { Net.json.parseToJsonElement(text).jsonObject } catch (e: Exception) { return }
        when (o["type"]?.jsonPrimitive?.contentOrNull) {
            "ready" -> {
                retry = 0
                val online = o["online"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
                _state.update { st ->
                    st.copy(conn = Conn.ONLINE, users = st.users.mapValues { (id, u) -> u.copy(online = id in online) })
                }
                scope.launch { afterReady() }
            }
            "call" -> _events.tryEmit(EngineEvent.Call(o))
            "message" -> {
                val m = Net.json.decodeFromJsonElement(Msg.serializer(), o["message"]!!)
                ingest(m, true)
                val key = convOf(m, me)
                if (m.sender_id != me && !m.deleted) _events.tryEmit(EngineEvent.Incoming(m, key))
                if (key == _state.value.active && m.sender_id != me && m.group_id == null) raw(buildJsonObject { put("type", "read"); put("peer", m.sender_id) })
                _state.update { st -> st.copy(typing = st.typing.mapValues { (k, v) -> if (k == key) v - m.sender_id else v }) }
            }
            "message_updated" -> ingest(Net.json.decodeFromJsonElement(Msg.serializer(), o["message"]!!), false)
            "status" -> {
                val ids = o["ids"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
                val status = o["status"]?.jsonPrimitive?.content ?: return
                _state.update { st -> st.copy(msgs = st.msgs.mapValues { (_, l) -> l.map { if (it.id in ids) it.copy(status = status) else it } }) }
            }
            "typing" -> {
                val from = o["from"]?.jsonPrimitive?.content ?: return
                val group = o["group"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                val on = o["typing"]?.jsonPrimitive?.booleanOrNull ?: true
                val key = if (group != null) "g:$group" else "u:$from"
                _state.update { st ->
                    val map = st.typing[key].orEmpty().toMutableMap()
                    if (on) map[from] = System.currentTimeMillis() + 5000 else map.remove(from)
                    st.copy(typing = st.typing + (key to map))
                }
            }
            "presence" -> {
                val id = o["user_id"]?.jsonPrimitive?.content ?: return
                val online = o["online"]?.jsonPrimitive?.booleanOrNull ?: false
                if (_state.value.users.containsKey(id)) _state.update { st -> st.copy(users = st.users + (id to st.users.getValue(id).copy(online = online))) }
                else refreshUsers()
            }
            "user_joined" -> {
                val u = Net.json.decodeFromJsonElement(UserInfo.serializer(), o["user"]!!)
                _state.update { it.copy(users = it.users + (u.id to u)) }
            }
            "group_added" -> {
                val g = Net.json.decodeFromJsonElement(GroupInfo.serializer(), o["group"]!!)
                _state.update { st -> st.copy(groups = st.groups.filter { it.id != g.id } + g) }
            }
            "group_removed" -> {
                val id = o["group_id"]?.jsonPrimitive?.content ?: return
                val key = "g:$id"
                loaded.remove(key)
                _state.update { st -> st.copy(groups = st.groups.filter { it.id != id }, msgs = st.msgs - key, unread = st.unread - key, active = if (st.active == key) null else st.active) }
            }
            "error" -> {
                val code = o["code"]?.jsonPrimitive?.content ?: "unknown"
                if (code == "unauthorized") {
                    running = false
                    ws?.close(1000, null)
                    _events.tryEmit(EngineEvent.Kicked)
                } else _events.tryEmit(EngineEvent.Error(code))
            }
        }
    }

    private fun ingest(m: Msg, counts: Boolean) {
        val s = session ?: return
        _state.update { st ->
            val key = convOf(m, s.userId)
            val list = st.msgs[key].orEmpty().toMutableList()
            val i = list.indexOfFirst { it.id == m.id }
            if (i >= 0) list[i] = m else list.add(m)
            list.sortBy { it.seq }
            val unread = if (counts && i < 0 && m.sender_id != s.userId && key != st.active && !m.deleted)
                st.unread + (key to ((st.unread[key] ?: 0) + 1)) else st.unread
            st.copy(msgs = st.msgs + (key to list), unread = unread)
        }
        if (m.seq > store.lastSeq(s)) store.saveSeq(s, m.seq)
    }

    private suspend fun afterReady() {
        val s = session ?: return
        try {
            refreshUsers()
            val groups = Net.json.decodeFromString(ListSerializer(GroupInfo.serializer()), Api.request(s.server, "/groups", token = s.token))
            _state.update { it.copy(groups = groups) }
            val missed = Net.json.decodeFromString(ListSerializer(Msg.serializer()), Api.request(s.server, "/sync?since=${store.lastSeq(s)}", token = s.token))
            missed.forEach { ingest(it, true) }
            try { hostIce = Net.json.decodeFromString(HostIce.serializer(), Api.request(s.server, "/ice", token = s.token)) } catch (e: Exception) { /* older host */ }
            _state.value.active?.let { loaded.remove(it); openConv(it) }
        } catch (e: Exception) {
            // The next reconnect syncs again.
        }
    }

    private suspend fun refreshUsers() {
        val s = session ?: return
        try {
            val users = Net.json.decodeFromString(ListSerializer(UserInfo.serializer()), Api.request(s.server, "/users", token = s.token))
            _state.update { st -> st.copy(users = users.associateBy { it.id }) }
        } catch (e: Exception) {
        }
    }

    // ---- actions ---------------------------------------------------------------------

    /** Opens a conversation: marks it active, clears unread and loads its history once. */
    suspend fun openConv(key: String) {
        val s = session ?: return
        _state.update { it.copy(active = key, unread = it.unread - key) }
        if (key.startsWith("u:")) raw(buildJsonObject { put("type", "read"); put("peer", key.drop(2)) })
        if (loaded.add(key)) {
            try {
                val q = if (key.startsWith("u:")) "peer=${key.drop(2)}" else "group=${key.drop(2)}"
                val list = Net.json.decodeFromString(ListSerializer(Msg.serializer()), Api.request(s.server, "/messages?$q&limit=100", token = s.token))
                list.forEach { ingest(it, false) }
            } catch (e: Exception) {
                loaded.remove(key)
            }
        }
    }

    fun closeConv() = _state.update { it.copy(active = null) }

    private fun target(key: String): JsonObject = buildJsonObject {
        if (key.startsWith("u:")) put("user", key.drop(2)) else put("group", key.drop(2))
    }

    fun send(key: String, body: String, replyTo: String? = null, fileId: String? = null): Boolean = raw(buildJsonObject {
        put("type", "send"); put("to", target(key)); put("body", body)
        if (replyTo != null) put("reply_to", replyTo)
        if (fileId != null) put("file_id", fileId)
        put("client_id", UUID.randomUUID().toString())
    })

    fun edit(id: String, body: String) = raw(buildJsonObject { put("type", "edit"); put("id", id); put("body", body) })
    fun remove(id: String) = raw(buildJsonObject { put("type", "delete"); put("id", id) })

    private var lastTyping = 0L
    fun typing(key: String) {
        val now = System.currentTimeMillis()
        if (now - lastTyping < 3000) return
        lastTyping = now
        raw(buildJsonObject { put("type", "typing"); put("to", target(key)); put("typing", true) })
    }

    fun sendCall(action: String, to: String, callId: String, video: Boolean, data: JsonObject? = null): Boolean = raw(buildJsonObject {
        put("type", "call"); put("action", action); put("to", to); put("call_id", callId); put("video", video)
        if (data != null) put("data", data)
    })

    suspend fun search(q: String): List<Msg> {
        val s = session ?: return emptyList()
        return Net.json.decodeFromString(ListSerializer(Msg.serializer()), Api.request(s.server, "/search?q=${URLEncoder.encode(q, "UTF-8")}", token = s.token))
    }

    suspend fun createGroup(name: String, memberIds: List<String>, kind: String = "group") {
        val s = session ?: return
        val body = buildJsonObject {
            put("name", name)
            put("kind", kind)
            put("member_ids", JsonArray(memberIds.map { JsonPrimitive(it) }))
        }.toString()
        val g = Net.json.decodeFromString(GroupInfo.serializer(), Api.request(s.server, "/groups", "POST", s.token, body))
        _state.update { st -> if (st.groups.any { it.id == g.id }) st else st.copy(groups = st.groups + g) }
    }

    suspend fun allGroups(): List<GroupInfo> {
        val s = session ?: return emptyList()
        return Net.json.decodeFromString(ListSerializer(GroupInfo.serializer()), Api.request(s.server, "/groups/all", token = s.token))
    }

    suspend fun joinGroup(id: String) {
        val s = session ?: return
        val g = Net.json.decodeFromString(GroupInfo.serializer(), Api.request(s.server, "/groups/$id/join", "POST", s.token, "{}"))
        _state.update { st -> st.copy(groups = st.groups.filter { it.id != g.id } + g) }
    }

    suspend fun leaveGroup(id: String) {
        val s = session ?: return
        Api.request(s.server, "/groups/$id/leave", "POST", s.token, "{}")
        _state.update { st -> st.copy(groups = st.groups.filter { it.id != id }, active = if (st.active == "g:$id") null else st.active) }
    }

    suspend fun deleteGroup(id: String) {
        val s = session ?: return
        Api.request(s.server, "/groups/$id", "DELETE", s.token, null)
        _state.update { st -> st.copy(groups = st.groups.filter { it.id != id }, active = if (st.active == "g:$id") null else st.active) }
    }

    fun react(id: String, emoji: String) = raw(buildJsonObject { put("type", "react"); put("id", id); put("emoji", emoji) })
    fun pin(id: String, on: Boolean) = raw(buildJsonObject { put("type", "pin"); put("id", id); put("on", on) })

    /** Channels are read-only for ordinary members. */
    fun canPost(key: String): Boolean {
        if (!key.startsWith("g:")) return true
        val g = _state.value.groups.firstOrNull { it.id == key.drop(2) } ?: return true
        return g.kind != "channel" || g.role == "owner" || g.role == "admin" || session?.role == "host" || session?.role == "admin"
    }

    fun canManage(key: String): Boolean {
        val g = _state.value.groups.firstOrNull { "g:${it.id}" == key } ?: return false
        return g.role == "owner" || g.role == "admin" || session?.role == "host" || session?.role == "admin"
    }

    /** Short-lived URL for streaming a video (the player sends the session token itself). */
    fun streamUrl(fileId: String): String = "${baseUrl(session?.server ?: "")}/api/v1/files/$fileId"

    fun typingNames(key: String): List<String> {
        val now = System.currentTimeMillis()
        return _state.value.typing[key].orEmpty().filter { it.value > now }.keys.map { _state.value.users[it]?.display_name ?: "…" }
    }

    // ---- files -----------------------------------------------------------------------

    private fun encodeName(name: String) = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun fileSize(uri: Uri): Long = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    fun displayName(uri: Uri): String = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file"
    } catch (e: Exception) {
        "file"
    }

    /** Uploads with progress, then sends a message carrying the file. Throws [ApiException] on refusal. */
    suspend fun sendFile(convKey: String, name: String, kind: String, size: Long, durationMs: Long? = null, open: () -> InputStream?) {
        val s = session ?: return
        val id = UUID.randomUUID().toString()
        fun progress(p: Float, failed: Boolean = false) = _state.update { st ->
            val others = st.uploads.filter { it.id != id }
            st.copy(uploads = if (p >= 1f && !failed) others else others + UploadItem(id, name, p, failed))
        }
        progress(0f)
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                val input = open() ?: throw IOException("cannot read file")
                input.use {
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        total += n
                        if (size > 0) progress((total.toFloat() / size).coerceAtMost(0.99f))
                    }
                }
            }
        }
        val req = Request.Builder().url("${baseUrl(s.server)}/api/v1/files")
            .header("Authorization", "Bearer ${s.token}")
            .header("X-File-Name", encodeName(name))
            .header("X-File-Kind", kind)
            .apply { if (durationMs != null && durationMs > 0) header("X-File-Duration", durationMs.toString()) }
            .post(body).build()
        try {
            val fileId = withContext(Dispatchers.IO) {
                val resp = try { Net.client.newCall(req).await() } catch (e: IOException) { throw ApiException("network", e.message ?: "network") }
                resp.use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw parseError(r.code, text)
                    Net.json.decodeFromString(FileInfo.serializer(), text).id
                }
            }
            if (!send(convKey, "", null, fileId)) throw ApiException("network", "not connected")
            progress(1f)
        } catch (e: Exception) {
            progress(0f, failed = true)
            scope.launch { delay(4000); _state.update { st -> st.copy(uploads = st.uploads.filter { it.id != id }) } }
            throw e
        }
    }

    suspend fun sendUri(convKey: String, uri: Uri) {
        val name = displayName(uri)
        val isVideo = app.contentResolver.getType(uri)?.startsWith("video/") == true
        sendFile(convKey, name, if (isVideo) "video" else "file", fileSize(uri), null) { app.contentResolver.openInputStream(uri) }
    }

    /** Streams a shared file into [out] (a location the user chose). */
    suspend fun downloadTo(fileId: String, out: OutputStream) = withContext(Dispatchers.IO) {
        val s = session ?: throw ApiException("network", "no session")
        val req = Request.Builder().url("${baseUrl(s.server)}/api/v1/files/$fileId").header("Authorization", "Bearer ${s.token}").build()
        val resp = try { Net.client.newCall(req).await() } catch (e: IOException) { throw ApiException("network", e.message ?: "network") }
        resp.use { r ->
            if (!r.isSuccessful) throw parseError(r.code, r.body?.string().orEmpty())
            r.body!!.byteStream().use { it.copyTo(out) }
        }
    }

    /** Image for a message, cached in memory and downsampled. */
    suspend fun loadImage(fileId: String): Bitmap? {
        images.get(fileId)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = java.io.ByteArrayOutputStream().also { downloadTo(fileId, it) }.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.also { images.put(fileId, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Downloads a voice note into the cache (once) and returns the file. */
    suspend fun voiceFile(fileId: String): java.io.File = withContext(Dispatchers.IO) {
        val f = java.io.File(app.cacheDir, "voice_$fileId.m4a")
        if (!f.exists() || f.length() == 0L) {
            val tmp = java.io.File(app.cacheDir, "voice_$fileId.part")
            tmp.outputStream().use { downloadTo(fileId, it) }
            tmp.renameTo(f)
        }
        f
    }
}
