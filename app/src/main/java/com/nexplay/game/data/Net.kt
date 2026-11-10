package com.nexplay.game.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object ServerUrl {
    fun http(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s
    }

    fun ws(raw: String): String {
        val h = http(raw)
        if (h.isEmpty()) return ""
        return (if (h.startsWith("https://")) "wss://" + h.removePrefix("https://") else "ws://" + h.removePrefix("http://")) + "/ws"
    }
}

class Api(private val http: OkHttpClient) {
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun request(base: String, method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val url = ServerUrl.http(base)
            if (url.isEmpty()) throw ApiException("Server address is empty")
            val rb = Request.Builder().url(url + path)
            if (!token.isNullOrEmpty()) rb.header("Authorization", "Bearer $token")
            if (method == "POST") rb.post((body ?: JSONObject()).toString().toRequestBody(json)) else rb.get()
            try {
                http.newCall(rb.build()).execute().use { resp: Response ->
                    val text = resp.body?.string().orEmpty()
                    val obj = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                    if (!resp.isSuccessful) throw ApiException(obj.optString("error", "HTTP ${resp.code}"))
                    obj
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException(e.message ?: "Network error")
            }
        }
}

data class OnlineRoom(
    val code: String,
    val game: String,
    val color: String,       // "w" or "b"
    val state: String,
    val turn: String,
    val oppName: String,
    val oppOnline: Boolean,
    val phase: String,       // waiting | playing | over
    val result: String?,     // "w" | "b" | "draw"
    val delta: Int,
    val last: String?,
    val seq: Int
)

enum class NetStatus { OFFLINE, CONNECTING, ONLINE }

/** WebSocket client with automatic reconnection. The server re-attaches the user to an active room after auth. */
class OnlineClient(private val repo: Repository, private val scope: CoroutineScope, private val http: OkHttpClient) {
    private val _status = MutableStateFlow(NetStatus.OFFLINE)
    val status: StateFlow<NetStatus> = _status
    private val _room = MutableStateFlow<OnlineRoom?>(null)
    val room: StateFlow<OnlineRoom?> = _room
    private val _queueing = MutableStateFlow(false)
    val queueing: StateFlow<Boolean> = _queueing
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    private var ws: WebSocket? = null
    private var manualClose = true
    private var attempt = 0
    private var lastRecordedKey = ""

    fun clearError() { _error.value = null }

    fun connect() {
        val p = repo.profile.value
        if (p.token.isEmpty() || p.serverUrl.isEmpty()) {
            _error.value = "Login required"
            return
        }
        manualClose = false
        open(ServerUrl.ws(p.serverUrl), p.token)
    }

    private fun open(url: String, token: String) {
        ws?.cancel()
        _status.value = NetStatus.CONNECTING
        val req = try { Request.Builder().url(url).build() } catch (e: Exception) {
            _status.value = NetStatus.OFFLINE
            _error.value = "Bad server address"
            return
        }
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                _reconnecting.value = false
                webSocket.send(JSONObject().put("t", "auth").put("token", token).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!manualClose) _error.value = t.message ?: "Connection failed"
                onDown()
            }
        })
    }

    private fun onDown() {
        _status.value = NetStatus.OFFLINE
        _queueing.value = false
        if (manualClose) return
        _reconnecting.value = true
        attempt++
        val wait = minOf(15000L, 1000L * attempt * attempt)
        scope.launch {
            delay(wait)
            if (!manualClose && _status.value == NetStatus.OFFLINE) {
                val p = repo.profile.value
                open(ServerUrl.ws(p.serverUrl), p.token)
            }
        }
    }

    private fun handle(text: String) {
        val o = try { JSONObject(text) } catch (_: Exception) { return }
        when (o.optString("t")) {
            "authed" -> _status.value = NetStatus.ONLINE
            "err" -> _error.value = o.optString("msg")
            "queued" -> _queueing.value = true
            "unqueued" -> _queueing.value = false
            "room" -> {
                _queueing.value = false
                val r = o.optJSONObject("room")
                if (r == null) {
                    _room.value = null
                } else {
                    val opp = r.optJSONObject("opp")
                    val room = OnlineRoom(
                        code = r.optString("code"),
                        game = r.optString("game"),
                        color = r.optString("color"),
                        state = r.optString("state"),
                        turn = r.optString("turn"),
                        oppName = opp?.optString("name").orEmpty(),
                        oppOnline = opp?.optBoolean("online", true) ?: false,
                        phase = r.optString("phase"),
                        result = if (r.isNull("result")) null else r.optString("result"),
                        delta = r.optInt("delta", 0),
                        last = if (r.isNull("last")) null else r.optString("last"),
                        seq = r.optInt("seq")
                    )
                    _room.value = room
                    if (room.phase == "over" && room.result != null) {
                        val key = room.code + ":" + room.result
                        if (key != lastRecordedKey) {
                            lastRecordedKey = key
                            val res = when (room.result) { "draw" -> "draw"; room.color -> "win"; else -> "loss" }
                            scope.launch { repo.recordResult(room.game, "online", res, room.oppName, -1) }
                        }
                    }
                }
            }
        }
    }

    private fun send(o: JSONObject): Boolean {
        val w = ws
        if (w == null || _status.value != NetStatus.ONLINE) {
            _error.value = "Not connected"
            return false
        }
        return w.send(o.toString())
    }

    fun createRoom(game: String) { send(JSONObject().put("t", "create").put("game", game)) }
    fun joinRoom(code: String) { send(JSONObject().put("t", "join").put("code", code.trim().uppercase())) }
    fun queue(game: String) { send(JSONObject().put("t", "queue").put("game", game)) }
    fun cancelQueue() { send(JSONObject().put("t", "unqueue")) }
    fun sendMove(move: JSONObject) { send(JSONObject().put("t", "move").put("move", move)) }
    fun resign() { send(JSONObject().put("t", "resign")) }
    fun leaveRoom() {
        send(JSONObject().put("t", "leave"))
        _room.value = null
    }

    fun disconnect() {
        manualClose = true
        _reconnecting.value = false
        ws?.close(1000, "bye")
        ws = null
        _status.value = NetStatus.OFFLINE
        _queueing.value = false
        _room.value = null
    }

    companion object {
        fun buildHttp(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

class Repository(private val prefs: Prefs, private val scope: CoroutineScope) {
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile

    private val http = OnlineClient.buildHttp()
    val api = Api(http)
    val online = OnlineClient(this, scope, http)

    init {
        scope.launch {
            prefs.profile.collect {
                _profile.value = it
                S.lang = it.lang
            }
        }
        scope.launch {
            // Welcome notification on first launch (empty history).
            kotlinx.coroutines.delay(800)
            val p = _profile.value
            if (p.notifs.isEmpty() && p.matches.isEmpty() && p.xp == 0) prefs.addNotif(S.t("welcome_notif"))
        }
    }

    fun launch(block: suspend () -> Unit) { scope.launch { block() } }

    suspend fun setLang(v: String) { prefs.setLang(v) }
    suspend fun setUsername(v: String) { prefs.setUsername(v) }
    suspend fun setAvatar(v: Int) { prefs.setAvatar(v) }
    suspend fun setSound(v: Boolean) { prefs.setSound(v) }
    suspend fun setServer(v: String) { prefs.setServer(v) }
    suspend fun setAuth(token: String, user: String) { prefs.setAuth(token, user) }
    suspend fun clearNotifs() { prefs.clearNotifs() }
    suspend fun claimDaily(bit: Int, xp: Int) { prefs.claimDaily(bit, xp) }
    suspend fun resetLocal() { prefs.resetLocal() }

    suspend fun recordResult(game: String, mode: String, result: String, opponent: String, aiLevel: Int) {
        prefs.record(game, mode, result, opponent, aiLevel)
        val before = _profile.value.daily
        // Notify when a daily challenge becomes completable.
        val w = before.wins + if (result == "win") 1 else 0
        if (w == 1 && result == "win") prefs.addNotif(S.t("daily_done"))
    }

    suspend fun login(register: Boolean, user: String, pass: String) {
        val base = _profile.value.serverUrl
        val body = JSONObject().put("username", user.trim()).put("password", pass)
        val r = api.request(base, "POST", if (register) "/api/register" else "/api/login", body)
        prefs.setAuth(r.getString("token"), r.optString("username", user.trim()))
    }

    suspend fun logout() {
        online.disconnect()
        prefs.setAuth("", "")
    }

    suspend fun leaderboard(game: String): List<LbEntry> {
        val p = _profile.value
        val r = api.request(p.serverUrl, "GET", "/api/leaderboard?game=$game")
        val arr = r.getJSONArray("players")
        val out = ArrayList<LbEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(LbEntry(o.optString("username"), o.optInt("avatar"), o.optInt("rating"), o.optInt("wins"), o.optInt("games"), o.optDouble("winRate")))
        }
        return out
    }

    suspend fun onlineCount(): Int? = try {
        val p = _profile.value
        if (p.serverUrl.isEmpty()) null else api.request(p.serverUrl, "GET", "/api/health").optInt("online", 0)
    } catch (_: Exception) { null }
}

data class LbEntry(val name: String, val avatar: Int, val rating: Int, val wins: Int, val games: Int, val winRate: Double)
