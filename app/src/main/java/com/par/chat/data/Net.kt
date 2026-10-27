package com.par.chat.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Net {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** WebSocket client: no read timeout, OkHttp pings detect dead connections (then we reconnect). */
    val wsClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/** Turns an error response into an [ApiException] carrying the host's error code. */
fun parseError(status: Int, text: String): ApiException {
    var code = "http_$status"
    var message = "HTTP $status"
    try {
        val err = Net.json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
        code = err?.get("code")?.jsonPrimitive?.content ?: code
        message = err?.get("message")?.jsonPrimitive?.content ?: message
    } catch (_: Exception) {
    }
    return ApiException(code, message)
}

object Api {
    /** JSON request against `http://<server>/api/v1<path>`. `server` is `host:port` ("[v6]:port" for IPv6). */
    suspend fun request(server: String, path: String, method: String = "GET", token: String? = null, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val builder = try {
                Request.Builder().url("http://$server/api/v1$path")
            } catch (e: IllegalArgumentException) {
                throw ApiException("invalid_input", "Invalid address")
            }
            if (token != null) builder.header("Authorization", "Bearer $token")
            if (body != null) builder.method(method, body.toRequestBody("application/json".toMediaType())) else builder.method(method, null)
            val resp = try {
                Net.client.newCall(builder.build()).await()
            } catch (e: IOException) {
                throw ApiException("network", e.message ?: "network error")
            }
            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw parseError(r.code, text)
                text
            }
        }

    /** Real connectivity test of one address: health endpoint (latency) + network identity check. */
    suspend fun testAddress(addr: String, expectedNetworkId: String?): AddrTest {
        val start = SystemClock.elapsedRealtime()
        return try {
            withTimeout(7000) {
                val health = Net.json.decodeFromString<HealthResp>(request(addr, "/health"))
                val latency = SystemClock.elapsedRealtime() - start
                if (health.service != "shabakeh-par-host" || health.status != "ok") return@withTimeout AddrTest(addr, false, latency, "not_par")
                val net = Net.json.decodeFromString<NetworkInfo>(request(addr, "/network"))
                if (expectedNetworkId != null && !net.network_id.equals(expectedNetworkId, true)) AddrTest(addr, false, latency, "mismatch")
                else AddrTest(addr, true, latency)
            }
        } catch (e: ApiException) {
            AddrTest(addr, false, 0, e.code)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AddrTest(addr, false, 0, "timeout")
        } catch (e: Exception) {
            AddrTest(addr, false, 0, "not_par")
        }
    }

    /** Tests every address at the same time; the caller picks the first working one in the invitation's order. */
    suspend fun testAll(addrs: List<String>, networkId: String?): List<AddrTest> = coroutineScope {
        addrs.map { async { testAddress(it, networkId) } }.map { it.await() }
    }

    suspend fun join(server: String, name: String, code: String, password: String?): Pair<Session, Boolean> {
        val body = Net.json.encodeToString(JoinReq.serializer(), JoinReq(name, code, password?.ifEmpty { null }))
        val resp = Net.json.decodeFromString<JoinResp>(request(server, "/join", "POST", null, body))
        val s = Session(server, resp.token, resp.user.id, resp.user.display_name, resp.user.role, resp.network.network_name, resp.network.network_id)
        return s to resp.pending
    }

    /** True while the host has not approved the membership. Throws [ApiException] ("unauthorized") if it was rejected. */
    suspend fun isPending(s: Session): Boolean =
        Net.json.decodeFromString<UserInfo>(request(s.server, "/me", token = s.token)).pending
}
