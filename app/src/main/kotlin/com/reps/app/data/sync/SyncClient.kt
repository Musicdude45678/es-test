package com.reps.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SyncException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Serializable
data class ServerInfo(
    val ok: Boolean = true,
    val user: String = "",
    val displayName: String = "",
    val snapshotCount: Int = 0,
    val latestSnapshotAt: Long? = null,
    val serverTime: Long = 0
)

@Serializable
data class PushResult(val ok: Boolean = true, val savedAt: Long = 0, val snapshotCount: Int = 0)

/** Minimal HTTP client for the Reps sync server. No third-party dependencies. */
class SyncClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun info(baseUrl: String, token: String): ServerInfo = withContext(Dispatchers.IO) {
        val (code, body) = request(baseUrl, "/api/me", "GET", token, null)
        if (code != 200) throw SyncException(describe(code, body))
        json.decodeFromString(ServerInfo.serializer(), body)
    }

    suspend fun push(baseUrl: String, token: String, snapshotJson: String): PushResult = withContext(Dispatchers.IO) {
        val (code, body) = request(baseUrl, "/api/snapshot", "PUT", token, snapshotJson)
        if (code != 200) throw SyncException(describe(code, body))
        json.decodeFromString(PushResult.serializer(), body)
    }

    /** Returns the latest snapshot JSON, or null when the server has none yet. */
    suspend fun latest(baseUrl: String, token: String): String? = withContext(Dispatchers.IO) {
        val (code, body) = request(baseUrl, "/api/snapshot/latest", "GET", token, null)
        when (code) {
            200 -> body
            404 -> null
            else -> throw SyncException(describe(code, body))
        }
    }

    private fun describe(code: Int, body: String): String = when (code) {
        401, 403 -> "Server rejected the sync token"
        404 -> "Server address not found (check the URL)"
        else -> "Server error $code" + body.take(120).let { if (it.isNotBlank()) ": $it" else "" }
    }

    private fun request(baseUrl: String, path: String, method: String, token: String, body: String?): Pair<Int, String> {
        val url = try { URL(baseUrl.trimEnd('/') + path) } catch (e: Exception) { throw SyncException("Invalid server URL") }
        val conn = try { url.openConnection() as HttpURLConnection } catch (e: Exception) { throw SyncException("Could not open connection", e) }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Reps-Android")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } catch (e: SyncException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw SyncException("No connection to server (offline?)", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw SyncException("Server timed out", e)
        } catch (e: javax.net.ssl.SSLException) {
            throw SyncException("Secure connection failed", e)
        } catch (e: Exception) {
            throw SyncException(e.message ?: "Network error", e)
        } finally {
            conn.disconnect()
        }
    }
}
