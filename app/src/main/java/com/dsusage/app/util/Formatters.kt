package com.dsusage.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val integerFormatter = NumberFormat.getIntegerInstance(Locale.CHINA)
    private val moneyFormatter = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun money(value: BigDecimal, currency: String = "CNY"): String {
        val symbol = when (currency) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> "$currency "
        }
        return "$symbol${moneyFormatter.format(value)}"
    }

    fun cost(value: BigDecimal): String = money(value, "CNY")

    fun integer(value: Long): String = integerFormatter.format(value)

    fun tokens(value: Long): String {
        val amount = value.toBigDecimal()
        return when {
            value >= 100_000_000 -> compact(amount.divide(BigDecimal("100000000"), 2, RoundingMode.HALF_UP)) + "亿"
            value >= 1_000_000 -> compact(amount.divide(BigDecimal("1000000"), 2, RoundingMode.HALF_UP)) + "M"
            value >= 10_000 -> compact(amount.divide(BigDecimal("1000"), 1, RoundingMode.HALF_UP)) + "K"
            value >= 1_000 -> compact(amount.divide(BigDecimal("1000"), 1, RoundingMode.HALF_UP)) + "K"
            else -> integer(value)
        }
    }

    fun tokensFull(value: Long): String = integer(value)

    fun time(timestamp: Long): String {
        if (timestamp <= 0L) return "--:--"
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }

    fun updated(timestamp: Long): String {
        if (timestamp <= 0L) return "尚未同步"
        return "${time(timestamp)} 更新"
    }

    private fun compact(value: BigDecimal): String {
        return value.stripTrailingZeros().toPlainString()
    }
}

