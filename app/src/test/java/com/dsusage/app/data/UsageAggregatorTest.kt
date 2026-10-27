package com.dsusage.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class UsageAggregatorTest {
    @Test
    fun aggregatesDashboardMetrics() {
        val summary = JSONObject(
            """
            {
              "normal_wallets": [{"balance":"20.00","currency":"CNY"}],
              "bonus_wallets": [{"balance":"10.91","currency":"CNY"}],
              "monthly_costs": [{"amount":"2.51","currency":"CNY"}]
            }
            """.trimIndent()
        )
        val cost = JSONObject(
            """
            {
              "data": [{
                "currency": "CNY",
                "total": [{
                  "model": "deepseek-chat",
                  "usage": [
                    {"type":"PROMPT_CACHE_HIT_TOKEN","amount":"0.60"},
                    {"type":"PROMPT_CACHE_MISS_TOKEN","amount":"0.31"},
                    {"type":"RESPONSE_TOKEN","amount":"1.60"}
                  ]
                }],
                "days": [
                  {"date":"2026-10-26","data":[{"model":"deepseek-chat","usage":[{"type":"RESPONSE_TOKEN","amount":"0.40"}]}]},
                  {"date":"2026-10-27","data":[{"model":"deepseek-chat","usage":[{"type":"RESPONSE_TOKEN","amount":"2.51"}]}]}
                ]
              }]
            }
            """.trimIndent()
        )
        val amount = JSONObject(
            """
            {
              "total": [{
                "model": "deepseek-chat",
                "usage": [
                  {"type":"REQUEST","amount":"186"},
                  {"type":"RESPONSE_TOKEN","amount":"12991546"},
                  {"type":"PROMPT_CACHE_MISS_TOKEN","amount":"20000000"},
                  {"type":"PROMPT_CACHE_HIT_TOKEN","amount":"10000000"}
                ]
              }],
              "days": [
                {"date":"2026-10-27","data":[{"model":"deepseek-chat","usage":[
                  {"type":"REQUEST","amount":"186"},
                  {"type":"RESPONSE_TOKEN","amount":"12991546"},
                  {"type":"PROMPT_CACHE_MISS_TOKEN","amount":"20000000"},
                  {"type":"PROMPT_CACHE_HIT_TOKEN","amount":"10000000"}
                ]}]}
              ]
            }
            """.trimIndent()
        )

        val result = UsageAggregator.aggregate(
            summary = summary,
            currentCost = cost,
            currentAmount = amount,
            today = LocalDate.of(2026, 10, 27),
            now = 1_700_000_000_000L
        )

        assertEquals(BigDecimal("30.91"), result.balance)
        assertEquals(BigDecimal("2.51"), result.todayCost)
        assertEquals(BigDecimal("2.51"), result.monthCost)
        assertEquals(186L, result.requestCount)
        assertEquals(42_991_546L, result.tokenCount)
        assertEquals(2, result.trend.size)
    }

    @Test
    fun handlesEmptyUsageWithoutFailing() {
        val result = UsageAggregator.aggregate(
            summary = JSONObject(),
            currentCost = JSONObject(),
            currentAmount = JSONObject(),
            today = LocalDate.of(2026, 10, 27)
        )

        assertEquals(0, result.balance.compareTo(BigDecimal.ZERO))
        assertEquals(0, result.todayCost.compareTo(BigDecimal.ZERO))
        assertEquals(0L, result.requestCount)
        assertEquals(0L, result.tokenCount)
    }

    @Test
    fun aggregatesCurrentByApiKeyResponse() {
        val zone = ZoneId.of("Asia/Shanghai")
        val todayEpoch = LocalDate.of(2026, 10, 27)
            .atStartOfDay(zone)
            .toEpochSecond()
        val yesterdayEpoch = LocalDate.of(2026, 10, 26)
            .atStartOfDay(zone)
            .toEpochSecond()
        val summary = JSONObject(
            """
            {
              "normal_wallets": [{"balance":"20.00","currency":"CNY"}],
              "bonus_wallets": [{"balance":"10.91","currency":"CNY"}],
              "total_costs": [{"amount":"74.16","currency":"CNY"}]
            }
            """.trimIndent()
        )
        val cost = JSONObject(
            """
            {
              "bucket": 86400,
              "data": [{
                "currency": "CNY",
                "series": [{
                  "api_key": {"tracking_id":"key-1","name":"app","sensitive_id":"***","valid":true},
                  "model": "deepseek-chat",
                  "buckets": [
                    {"time":$yesterdayEpoch,"cost":"0.40"},
                    {"time":$todayEpoch,"cost":"2.51"}
                  ]
                }]
              }]
            }
            """.trimIndent()
        )
        val amount = JSONObject(
            """
            {
              "bucket": 86400,
              "series": [{
                "api_key": {"tracking_id":"key-1","name":"app","sensitive_id":"***","valid":true},
                "model": "deepseek-chat",
                "buckets": [{
                  "time":$todayEpoch,
                  "usage": {
                    "REQUEST": 186,
                    "RESPONSE_TOKEN": 12991546,
                    "PROMPT_CACHE_MISS_TOKEN": 20000000,
                    "PROMPT_CACHE_HIT_TOKEN": 10000000
                  }
                }]
              }]
            }
            """.trimIndent()
        )

        val result = UsageAggregator.aggregate(
            summary = summary,
            currentCost = cost,
            currentAmount = amount,
            today = LocalDate.of(2026, 10, 27),
            now = 1_700_000_000_000L,
            zoneId = zone
        )

        assertEquals(BigDecimal("30.91"), result.balance)
        assertEquals(BigDecimal("74.16"), result.totalCost)
        assertEquals(BigDecimal("2.51"), result.todayCost)
        assertEquals(186L, result.requestCount)
        assertEquals(42_991_546L, result.tokenCount)
        assertEquals(2, result.trend.size)
    }

    @Test
    fun snapshotRoundTripsThroughJson() {
        val original = UsageSnapshot(
            balance = BigDecimal("30.91"),
            todayCost = BigDecimal("2.51"),
            requestCount = 186,
            tokenCount = 42_991_546,
            trend = listOf(DayUsage(LocalDate.of(2026, 10, 27), BigDecimal("2.51"))),
            updatedAt = 1_700_000_000_000L
        )

        val restored = UsageSnapshot.fromJson(original.toJson())

        assertEquals(original, restored)
    }
}
