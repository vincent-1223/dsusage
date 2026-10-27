package com.dsusage.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

data class DayUsage(
    val date: LocalDate,
    val cost: BigDecimal
)

data class UsageSnapshot(
    val balance: BigDecimal = BigDecimal.ZERO,
    val currency: String = "CNY",
    val todayCost: BigDecimal = BigDecimal.ZERO,
    val totalCost: BigDecimal = BigDecimal.ZERO,
    val monthCost: BigDecimal = BigDecimal.ZERO,
    val requestCount: Long = 0L,
    val tokenCount: Long = 0L,
    val trend: List<DayUsage> = emptyList(),
    val updatedAt: Long = 0L,
    val error: String? = null,
    val sessionExpired: Boolean = false
) {
    val hasData: Boolean
        get() = updatedAt > 0L

    fun toJson(): String = JSONObject().apply {
        put("balance", balance.toPlainString())
        put("currency", currency)
        put("todayCost", todayCost.toPlainString())
        put("totalCost", totalCost.toPlainString())
        put("monthCost", monthCost.toPlainString())
        put("requestCount", requestCount)
        put("tokenCount", tokenCount)
        put("updatedAt", updatedAt)
        if (error == null) remove("error") else put("error", error)
        put("sessionExpired", sessionExpired)
        put("trend", JSONArray().apply {
            trend.forEach {
                put(
                    JSONObject()
                        .put("date", it.date.toString())
                        .put("cost", it.cost.toPlainString())
                )
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String): UsageSnapshot = runCatching {
            val root = JSONObject(json)
            val trendArray = root.optJSONArray("trend") ?: JSONArray()
            val trend = buildList {
                for (index in 0 until trendArray.length()) {
                    val item = trendArray.getJSONObject(index)
                    add(
                        DayUsage(
                            date = LocalDate.parse(item.getString("date")),
                            cost = item.optString("cost").toBigDecimalOrNull()
                                ?: BigDecimal.ZERO
                        )
                    )
                }
            }
            UsageSnapshot(
                balance = root.optString("balance").toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currency = root.optString("currency", "CNY"),
                todayCost = root.optString("todayCost").toBigDecimalOrNull()
                    ?: BigDecimal.ZERO,
                totalCost = root.optString("totalCost").toBigDecimalOrNull()
                    ?: BigDecimal.ZERO,
                monthCost = root.optString("monthCost").toBigDecimalOrNull()
                    ?: BigDecimal.ZERO,
                requestCount = root.optLong("requestCount", 0L),
                tokenCount = root.optLong("tokenCount", 0L),
                trend = trend,
                updatedAt = root.optLong("updatedAt", 0L),
                error = root.optString("error").takeIf { it.isNotBlank() },
                sessionExpired = root.optBoolean("sessionExpired", false)
            )
        }.getOrDefault(UsageSnapshot())
    }
}

