package com.dsusage.app.network

import com.dsusage.app.BuildConfig
import com.dsusage.app.data.UsageAggregator
import com.dsusage.app.data.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import java.util.concurrent.TimeUnit

class SessionExpiredException(message: String) : IOException(message)

class UsageApiException(message: String) : IOException(message)

class DeepSeekUsageClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun verifySession(token: String) = withContext(Dispatchers.IO) {
        fetchData("/api/v0/users/get_user_summary", token)
        true
    }

    suspend fun fetchUsage(token: String): UsageSnapshot = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rangeStart = today.minusDays(6).atStartOfDay(zone).toEpochSecond()
        val rangeEnd = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val tzOffset = zone.rules.getOffset(Instant.now()).totalSeconds
        val hourAlignedOffset = floor(tzOffset / 3600.0).toInt() * 3600
        val remainder = tzOffset - hourAlignedOffset
        val rangeQuery =
            "start=${rangeStart + remainder}&end=${rangeEnd + remainder}&tz=$hourAlignedOffset"

        val summary = fetchData("/api/v0/users/get_user_summary", token)
        val currentCost = fetchData(
            "/api/v0/usage/by_api_key/cost?$rangeQuery",
            token
        )
        val currentAmount = fetchData(
            "/api/v0/usage/by_api_key/amount?$rangeQuery",
            token
        )

        UsageAggregator.aggregate(
            summary = summary,
            currentCost = currentCost,
            currentAmount = currentAmount,
            today = today,
            zoneId = zone
        )
    }

    private fun fetchData(path: String, token: String): JSONObject {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Referer", "$BASE_URL/usage")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36 DSUsage/${BuildConfig.VERSION_NAME}"
            )
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UsageApiException("DeepSeek 服务返回 HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = runCatching { JSONObject(body) }
                .getOrElse { throw UsageApiException("DeepSeek 返回了无法识别的数据") }
            val code = root.optInt("code", 0)
            if (code == CODE_MISSING_TOKEN || code == CODE_INVALID_TOKEN) {
                throw SessionExpiredException("登录已过期，请重新登录")
            }
            if (code != 0) {
                throw UsageApiException(
                    root.optString("msg").ifBlank { "DeepSeek 接口返回错误 $code" }
                )
            }
            val data = root.optJSONObject("data") ?: JSONObject()
            return data.optJSONObject("biz_data") ?: data
        }
    }

    private companion object {
        const val BASE_URL = "https://platform.deepseek.com"
        const val CODE_MISSING_TOKEN = 40002
        const val CODE_INVALID_TOKEN = 40003

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

