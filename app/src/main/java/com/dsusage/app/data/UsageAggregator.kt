package com.dsusage.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageAggregator {
    fun aggregate(
        summary: JSONObject,
        currentCost: JSONObject,
        currentAmount: JSONObject,
        extraCosts: List<JSONObject> = emptyList(),
        extraAmounts: List<JSONObject> = emptyList(),
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): UsageSnapshot {
        val walletCurrency = preferredCurrency(
            summary.optJSONArray("normal_wallets"),
            summary.optJSONArray("bonus_wallets"),
            summary.optJSONArray("monthly_costs")
        )
        val balance = walletTotal(summary.optJSONArray("normal_wallets"), walletCurrency) +
            walletTotal(summary.optJSONArray("bonus_wallets"), walletCurrency)
        val totalCost = walletCurrencyAmount(
            summary.optJSONArray("total_costs"),
            walletCurrency
        )

        val currentCostCurrency = preferredCostCurrency(currentCost, walletCurrency)
        val monthCost = legacyTotalCost(currentCost, currentCostCurrency)
        val dailyCosts = dailyCosts(currentCost, currentCostCurrency, zoneId)
        val todayCost = dailyCosts[today] ?: BigDecimal.ZERO
        val requestCount = currentAmountRequests(currentAmount, today, zoneId)
        val tokenCount = currentAmountTokens(currentAmount, today, zoneId)

        val mergedDailyCosts = buildMap<LocalDate, BigDecimal> {
            putAll(dailyCosts)
            buildList {
                addAll(extraCosts)
            }.forEach { data ->
                val currencyData = currencyObject(data, currentCostCurrency) ?: return@forEach
                val days = currencyData.optJSONArray("days") ?: return@forEach
                for (index in 0 until days.length()) {
                    val day = days.optJSONObject(index) ?: continue
                    val date = parseDate(day.optString("date")) ?: continue
                    if (date.isAfter(today)) continue
                    put(date, sumDayCost(day))
                }
            }
        }
        val trend = mergedDailyCosts.toSortedMap().entries.toList()
            .takeLast(7)
            .map { DayUsage(it.key, it.value) }

        return UsageSnapshot(
            balance = balance.setScale(2, RoundingMode.HALF_UP),
            currency = walletCurrency.ifBlank { currentCostCurrency.ifBlank { "CNY" } },
            todayCost = todayCost.setScale(2, RoundingMode.HALF_UP),
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP),
            monthCost = monthCost.setScale(2, RoundingMode.HALF_UP),
            requestCount = requestCount,
            tokenCount = tokenCount,
            trend = trend,
            updatedAt = now
        )
    }

    private fun preferredCurrency(vararg arrays: JSONArray?): String {
        val currencies = arrays.filterNotNull().flatMap { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.optString("currency")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }
        return if ("CNY" in currencies) "CNY" else currencies.firstOrNull().orEmpty()
    }

    private fun walletTotal(wallets: JSONArray?, currency: String): BigDecimal {
        if (wallets == null) return BigDecimal.ZERO
        val target = currency.ifBlank {
            wallets.optJSONObject(0)?.optString("currency").orEmpty()
        }
        return (0 until wallets.length())
            .mapNotNull { wallets.optJSONObject(it) }
            .filter { it.optString("currency") == target }
            .fold(BigDecimal.ZERO) { total, wallet ->
                total + parseDecimal(wallet.opt("balance"))
            }
    }

    private fun walletCurrencyAmount(items: JSONArray?, currency: String): BigDecimal {
        if (items == null) return BigDecimal.ZERO
        val target = if (currency.isBlank()) {
            items.optJSONObject(0)?.optString("currency").orEmpty()
        } else {
            currency
        }
        return (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("currency") == target }
            .fold(BigDecimal.ZERO) { total, item ->
                total + parseDecimal(item.opt("amount"))
            }
    }

    private fun preferredCostCurrency(cost: JSONObject, fallback: String): String {
        val currencies = cost.optJSONArray("data") ?: return fallback
        val values = (0 until currencies.length())
            .mapNotNull { currencies.optJSONObject(it)?.optString("currency") }
        return when {
            fallback.isNotBlank() && fallback in values -> fallback
            "CNY" in values -> "CNY"
            else -> values.firstOrNull() ?: fallback
        }
    }

    private fun currencyObject(cost: JSONObject, currency: String): JSONObject? {
        val currencies = cost.optJSONArray("data") ?: return null
        for (index in 0 until currencies.length()) {
            val item = currencies.optJSONObject(index) ?: continue
            if (item.optString("currency") == currency) return item
        }
        return currencies.optJSONObject(0)
    }

    private fun legacyTotalCost(cost: JSONObject, currency: String): BigDecimal {
        val currencyObject = currencyObject(cost, currency) ?: return BigDecimal.ZERO
        return totalUsage(currencyObject.optJSONArray("total"))
    }

    private fun totalUsage(items: JSONArray?): BigDecimal {
        if (items == null) return BigDecimal.ZERO
        var total = BigDecimal.ZERO
        for (index in 0 until items.length()) {
            val usage = items.optJSONObject(index)?.optJSONArray("usage") ?: continue
            for (usageIndex in 0 until usage.length()) {
                total += parseDecimal(usage.optJSONObject(usageIndex)?.opt("amount"))
            }
        }
        return total
    }

    private fun dailyCosts(
        cost: JSONObject,
        currency: String,
        zoneId: ZoneId
    ): Map<LocalDate, BigDecimal> {
        val data = currencyObject(cost, currency) ?: return emptyMap()
        val result = linkedMapOf<LocalDate, BigDecimal>()

        val series = data.optJSONArray("series")
        if (series != null) {
            for (seriesIndex in 0 until series.length()) {
                val buckets = series.optJSONObject(seriesIndex)?.optJSONArray("buckets")
                    ?: continue
                for (bucketIndex in 0 until buckets.length()) {
                    val bucket = buckets.optJSONObject(bucketIndex) ?: continue
                    val date = epochDate(bucket.optLong("time", -1L), zoneId) ?: continue
                    result[date] = (result[date] ?: BigDecimal.ZERO) +
                        parseDecimal(bucket.opt("cost"))
                }
            }
            return result
        }

        val days = data.optJSONArray("days") ?: return result
        for (index in 0 until days.length()) {
            val day = days.optJSONObject(index) ?: continue
            val date = parseDate(day.optString("date")) ?: continue
            result[date] = sumDayCost(day)
        }
        return result
    }

    private fun sumDayCost(day: JSONObject): BigDecimal {
        val groups = day.optJSONArray("data") ?: return BigDecimal.ZERO
        var total = BigDecimal.ZERO
        for (index in 0 until groups.length()) {
            val usage = groups.optJSONObject(index)?.optJSONArray("usage") ?: continue
            for (usageIndex in 0 until usage.length()) {
                total += parseDecimal(usage.optJSONObject(usageIndex)?.opt("amount"))
            }
        }
        return total
    }

    private fun requestCountForDate(amount: JSONObject, date: LocalDate): Long {
        val days = amount.optJSONArray("data")?.let { outer ->
            findDay(outer, date)
        } ?: findDay(amount.optJSONArray("days"), date)
        if (days == null) return 0L
        return sumAmountTypes(days, setOf("REQUEST")).toLong()
    }

    private fun currentAmountRequests(
        amount: JSONObject,
        date: LocalDate,
        zoneId: ZoneId
    ): Long {
        val series = amount.optJSONArray("series")
        if (series == null) return requestCountForDate(amount, date)
        var total = BigDecimal.ZERO
        for (seriesIndex in 0 until series.length()) {
            val buckets = series.optJSONObject(seriesIndex)?.optJSONArray("buckets")
                ?: continue
            for (bucketIndex in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(bucketIndex) ?: continue
                if (epochDate(bucket.optLong("time", -1L), zoneId) != date) continue
                total += parseDecimal(bucket.optJSONObject("usage")?.opt("REQUEST"))
            }
        }
        return total.toLong()
    }

    private fun tokenCountForDate(amount: JSONObject, date: LocalDate): Long {
        val days = amount.optJSONArray("data")?.let { outer ->
            findDay(outer, date)
        } ?: findDay(amount.optJSONArray("days"), date)
        if (days == null) return 0L
        return sumAmountTypes(
            days,
            setOf("RESPONSE_TOKEN", "PROMPT_CACHE_MISS_TOKEN", "PROMPT_CACHE_HIT_TOKEN")
        ).toLong()
    }

    private fun currentAmountTokens(
        amount: JSONObject,
        date: LocalDate,
        zoneId: ZoneId
    ): Long {
        val series = amount.optJSONArray("series")
        if (series == null) return tokenCountForDate(amount, date)
        var total = BigDecimal.ZERO
        for (seriesIndex in 0 until series.length()) {
            val buckets = series.optJSONObject(seriesIndex)?.optJSONArray("buckets")
                ?: continue
            for (bucketIndex in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(bucketIndex) ?: continue
                if (epochDate(bucket.optLong("time", -1L), zoneId) != date) continue
                val usage = bucket.optJSONObject("usage") ?: continue
                total += parseDecimal(usage.opt("RESPONSE_TOKEN"))
                total += parseDecimal(usage.opt("PROMPT_CACHE_MISS_TOKEN"))
                total += parseDecimal(usage.opt("PROMPT_CACHE_HIT_TOKEN"))
            }
        }
        return total.toLong()
    }

    /**
     * The amount endpoint returns {days:[...]}. Keep tolerance for an
     * intermediate wrapper used by some platform builds.
     */
    private fun findDay(items: JSONArray?, date: LocalDate): JSONObject? {
        if (items == null) return null
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.has("date") && parseDate(item.optString("date")) == date) return item
            val nestedDays = item.optJSONArray("days") ?: continue
            for (nestedIndex in 0 until nestedDays.length()) {
                val day = nestedDays.optJSONObject(nestedIndex) ?: continue
                if (parseDate(day.optString("date")) == date) return day
            }
        }
        return null
    }

    private fun sumAmountTypes(day: JSONObject, types: Set<String>): BigDecimal {
        val groups = day.optJSONArray("data") ?: return BigDecimal.ZERO
        var total = BigDecimal.ZERO
        for (index in 0 until groups.length()) {
            val usage = groups.optJSONObject(index)?.optJSONArray("usage") ?: continue
            for (usageIndex in 0 until usage.length()) {
                val item = usage.optJSONObject(usageIndex) ?: continue
                if (item.optString("type") in types) {
                    total += parseDecimal(item.opt("amount"))
                }
            }
        }
        return total
    }

    private fun parseDate(raw: String): LocalDate? {
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    private fun epochDate(epochSeconds: Long, zoneId: ZoneId): LocalDate? {
        if (epochSeconds <= 0L) return null
        return Instant.ofEpochSecond(epochSeconds).atZone(zoneId).toLocalDate()
    }

    private fun parseDecimal(value: Any?): BigDecimal {
        return when (value) {
            null, JSONObject.NULL -> BigDecimal.ZERO
            is BigDecimal -> value
            is Number -> value.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
            else -> value.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
    }
}

