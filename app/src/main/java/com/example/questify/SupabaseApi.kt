package com.example.questify

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SupabaseApi {
    private val gson = Gson()
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun parseUtc(value: String, pattern: String): Long? {
        val fmt = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }
        return runCatching { fmt.parse(value)?.time }.getOrNull()
    }

    private fun toEpochMillis(ts: String?): Long {
        if (ts.isNullOrBlank()) return System.currentTimeMillis()
        return parseUtc(ts, "yyyy-MM-dd'T'HH:mm:ss.SSSX")
            ?: parseUtc(ts, "yyyy-MM-dd'T'HH:mm:ssX")
            ?: System.currentTimeMillis()
    }

    private fun toIso(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }
        return fmt.format(Date(millis))
    }

    private fun openConnection(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        bearerToken: String? = null
    ): HttpURLConnection {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val queryPart = if (query.isEmpty()) "" else query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val full = if (queryPart.isBlank()) "$base$path" else "$base$path?$queryPart"
        val conn = URL(full).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        val token = bearerToken ?: BuildConfig.SUPABASE_ANON_KEY
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json")
        return conn
    }

    private suspend fun Request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
        prefer: String? = null,
        bearerToken: String? = null
    ): Pair<Int, String> {
        var backoffMs = 300L
        var lastCode = 0
        var lastRaw = ""
        repeat(3) { attempt ->
            val call = runCatching {
                val conn = openConnection(method, path, query, bearerToken)
                if (!prefer.isNullOrBlank()) conn.setRequestProperty("Prefer", prefer)
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                }
                val code = conn.responseCode
                val raw = runCatching {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                conn.disconnect()
                code to raw
            }
            if (call.isSuccess) {
                val (code, raw) = call.getOrThrow()
                lastCode = code
                lastRaw = raw
                if (code in 200..299 || (code in 400..499 && code != 429)) {
                    return code to raw
                }
            } else {
                val ex = call.exceptionOrNull()
                if (ex !is IOException) return 0 to ""
            }
            if (attempt < 2) {
                delay(backoffMs)
                backoffMs *= 2L
            }
        }
        return lastCode to lastRaw
    }

    suspend fun signInWithEmail(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured || email.isBlank() || password.isBlank()) return@withContext null
        val body = gson.toJson(mapOf("email" to email.trim(), "password" to password))
        val (code, raw) = Request(
            method = "POST",
            path = "/auth/v1/token",
            query = mapOf("grant_type" to "password"),
            body = body
        )
        if (code !in 200..299 || raw.isBlank()) return@withContext null
        runCatching {
            gson.fromJson(raw, AuthSessionResponse::class.java)?.accessToken
        }.getOrNull()
    }

    suspend fun signUpWithEmail(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured || email.isBlank() || password.isBlank()) return@withContext null
        val body = gson.toJson(mapOf("email" to email.trim(), "password" to password))
        val (code, raw) = Request(
            method = "POST",
            path = "/auth/v1/signup",
            body = body
        )
        if (code !in 200..299 || raw.isBlank()) return@withContext null
        runCatching {
            gson.fromJson(raw, AuthSessionResponse::class.java)?.accessToken
        }.getOrNull()
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSessionResponse? = withContext(Dispatchers.IO) {
        if (!isConfigured || idToken.isBlank()) return@withContext null
        val body = gson.toJson(mapOf("id_token" to idToken, "provider" to "google"))
        val (code, raw) = Request(
            method = "POST",
            path = "/auth/v1/token",
            query = mapOf("grant_type" to "id_token"),
            body = body
        )
        if (code !in 200..299 || raw.isBlank()) return@withContext null
        runCatching {
            gson.fromJson(raw, AuthSessionResponse::class.java)
        }.getOrNull()
    }

    suspend fun getUser(accessToken: String): AuthUser? = withContext(Dispatchers.IO) {
        if (!isConfigured || accessToken.isBlank()) return@withContext null
        val (code, raw) = Request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = accessToken
        )
        if (code !in 200..299 || raw.isBlank()) return@withContext null
        runCatching {
            gson.fromJson(raw, AuthUser::class.java)
        }.getOrNull()
    }

    suspend fun signOut(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || accessToken.isBlank()) return@withContext false
        val (code, _) = Request(
            method = "POST",
            path = "/auth/v1/logout",
            bearerToken = accessToken
        )
        code in 200..299
    }

    suspend fun upsertCloudBackup(userEmail: String, payload: String, accessToken: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || userEmail.isBlank() || payload.isBlank() || accessToken.isBlank()) return@withContext false
        val body = gson.toJson(
            listOf(
                mapOf(
                    "user_email" to userEmail.trim().lowercase(Locale.getDefault()),
                    "payload" to payload,
                    "updated_at" to toIso(System.currentTimeMillis())
                )
            )
        )
        val (code, raw) = Request(
            method = "POST",
            path = "/rest/v1/cloud_backups",
            query = mapOf("on_conflict" to "user_email"),
            body = body,
            prefer = "resolution=merge-duplicates",
            bearerToken = accessToken
        )
        if (code !in 200..299) AppLog.w("Cloud backup upsert failed code=$code raw=$raw")
        code in 200..299
    }

    suspend fun fetchCloudBackup(userEmail: String, accessToken: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured || userEmail.isBlank() || accessToken.isBlank()) return@withContext null
        val (code, raw) = Request(
            method = "GET",
            path = "/rest/v1/cloud_backups",
            query = mapOf(
                "select" to "payload",
                "user_email" to "eq.${userEmail.trim().lowercase(Locale.getDefault())}",
                "order" to "updated_at.desc",
                "limit" to "1"
            ),
            bearerToken = accessToken
        )
        if (code !in 200..299 || raw.isBlank()) return@withContext null
        runCatching {
            gson.fromJson(raw, Array<CloudBackupRow>::class.java)?.firstOrNull()?.payload
        }.getOrNull()
    }

    suspend fun submitFeedbackInbox(
        userId: String,
        userName: String,
        category: String,
        message: String,
        appTheme: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || userId.isBlank() || message.isBlank()) return@withContext false
        val payload = mapOf(
            "user_id" to userId.trim(),
            "user_name" to userName.trim().ifBlank { "Player" },
            "category" to category.trim().ifBlank { "General" },
            "message" to message.trim(),
            "app_theme" to appTheme.trim(),
            "created_at" to toIso(System.currentTimeMillis())
        )
        val body = gson.toJson(listOf(payload))
        val (code, raw) = Request(
            method = "POST",
            path = "/rest/v1/app_feedback_inbox",
            body = body
        )
        if (code !in 200..299) AppLog.w("Feedback submit failed code=$code raw=$raw")
        code in 200..299
    }

    suspend fun upsertPlanState(
        userId: String,
        userName: String,
        plans: Map<Long, List<String>>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || userId.isBlank()) return@withContext false
        val payload = mapOf(
            "user_id" to userId.trim(),
            "user_name" to userName.trim().ifBlank { "Player" },
            "plans_json" to gson.toJson(plans),
            "updated_at" to toIso(System.currentTimeMillis())
        )
        val (code, raw) = Request(
            method = "POST",
            path = "/rest/v1/app_plan_states",
            query = mapOf("on_conflict" to "user_id"),
            body = gson.toJson(listOf(payload)),
            prefer = "resolution=merge-duplicates"
        )
        if (code !in 200..299) AppLog.w("Plan sync failed code=$code raw=$raw")
        code in 200..299
    }

    suspend fun upsertHistoryState(
        userId: String,
        userName: String,
        day: Long,
        done: Int,
        total: Int,
        allDone: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || userId.isBlank()) return@withContext false
        val payload = mapOf(
            "user_id" to userId.trim(),
            "user_name" to userName.trim().ifBlank { "Player" },
            "epoch_day" to day,
            "done_count" to done.coerceAtLeast(0),
            "total_count" to total.coerceAtLeast(0),
            "all_done" to allDone,
            "updated_at" to toIso(System.currentTimeMillis())
        )
        val (code, raw) = Request(
            method = "POST",
            path = "/rest/v1/app_history_states",
            query = mapOf("on_conflict" to "user_id,epoch_day"),
            body = gson.toJson(listOf(payload)),
            prefer = "resolution=merge-duplicates"
        )
        if (code !in 200..299) AppLog.w("History sync failed code=$code raw=$raw")
        code in 200..299
    }

    data class AuthSessionResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        val user: AuthUser? = null
    )

    data class AuthUser(
        val id: String?,
        val email: String?
    )

    private data class CloudBackupRow(val payload: String)
}

























