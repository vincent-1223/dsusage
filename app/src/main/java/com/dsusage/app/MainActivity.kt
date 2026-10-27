package com.dsusage.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dsusage.app.data.SecureTokenStore
import com.dsusage.app.data.UsageSnapshot
import com.dsusage.app.data.UsageStore
import com.dsusage.app.sync.UsageSyncManager
import com.dsusage.app.sync.UsageSyncScheduler
import com.dsusage.app.ui.UsageTrendView
import com.dsusage.app.util.Formatters
import com.dsusage.app.widget.CompactUsageWidgetProvider
import com.dsusage.app.widget.DashboardUsageWidgetProvider
import com.dsusage.app.widget.StandardUsageWidgetProvider
import com.dsusage.app.widget.UsageWidgetUpdater
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var usageStore: UsageStore

    private lateinit var connectionDot: View
    private lateinit var connectionTitle: TextView
    private lateinit var connectionDetail: TextView
    private lateinit var statusText: TextView
    private lateinit var balanceValue: TextView
    private lateinit var balanceCurrency: TextView
    private lateinit var metricTodayCost: TextView
    private lateinit var metricRequests: TextView
    private lateinit var metricTokens: TextView
    private lateinit var monthCost: TextView
    private lateinit var lastUpdated: TextView
    private lateinit var trendView: UsageTrendView
    private lateinit var errorBanner: TextView
    private lateinit var loginButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var logoutButton: MaterialButton

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            requestRefresh()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tokenStore = SecureTokenStore(this)
        usageStore = UsageStore(this)

        connectionDot = findViewById(R.id.connection_dot)
        connectionTitle = findViewById(R.id.connection_title)
        connectionDetail = findViewById(R.id.connection_detail)
        statusText = findViewById(R.id.status_text)
        balanceValue = findViewById(R.id.balance_value)
        balanceCurrency = findViewById(R.id.balance_currency)
        metricTodayCost = findViewById(R.id.metric_today_cost)
        metricRequests = findViewById(R.id.metric_requests)
        metricTokens = findViewById(R.id.metric_tokens)
        monthCost = findViewById(R.id.month_cost)
        lastUpdated = findViewById(R.id.last_updated)
        trendView = findViewById(R.id.trend_view)
        errorBanner = findViewById(R.id.error_banner)
        loginButton = findViewById(R.id.login_button)
        refreshButton = findViewById(R.id.refresh_button)
        logoutButton = findViewById(R.id.logout_button)

        loginButton.setOnClickListener { openLogin() }
        refreshButton.setOnClickListener { requestRefresh() }
        logoutButton.setOnClickListener { confirmLogout() }

        findViewById<MaterialButton>(R.id.add_compact_widget_button).setOnClickListener {
            requestWidgetPin(CompactUsageWidgetProvider::class.java)
        }
        findViewById<MaterialButton>(R.id.add_standard_widget_button).setOnClickListener {
            requestWidgetPin(StandardUsageWidgetProvider::class.java)
        }
        findViewById<MaterialButton>(R.id.add_dashboard_widget_button).setOnClickListener {
            requestWidgetPin(DashboardUsageWidgetProvider::class.java)
        }
    }

    override fun onResume() {
        super.onResume()
        UsageWidgetUpdater.updateAll(this)
        render()
    }

    private fun render() {
        val hasToken = tokenStore.hasToken()
        val snapshot = usageStore.load()

        if (snapshot.hasData) {
            balanceValue.text = Formatters.money(snapshot.balance, snapshot.currency)
            balanceCurrency.text = snapshot.currency
            metricTodayCost.text = Formatters.money(snapshot.todayCost, snapshot.currency)
            metricRequests.text = Formatters.integer(snapshot.requestCount)
            metricTokens.text = Formatters.tokens(snapshot.tokenCount)
            monthCost.text = "累计 ${Formatters.money(snapshot.totalCost, snapshot.currency)}"
            lastUpdated.text = Formatters.updated(snapshot.updatedAt)
            trendView.submit(snapshot)
        } else {
            balanceValue.text = "--"
            balanceCurrency.text = "CNY"
            metricTodayCost.text = "--"
            metricRequests.text = "--"
            metricTokens.text = "--"
            monthCost.text = "累计 --"
            lastUpdated.text = "尚未同步"
            trendView.submit(UsageSnapshot())
        }

        errorBanner.visibility = if (snapshot.error == null) View.GONE else View.VISIBLE
        errorBanner.text = snapshot.error.orEmpty()

        when {
            !hasToken -> {
                connectionTitle.text = "未连接"
                connectionDetail.text = "在 App 内登录 DeepSeek，登录令牌只保存在本机。"
                statusText.text = "等待登录"
                connectionDot.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_status_dot_warn
                )
                statusText.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary))
            }
            snapshot.sessionExpired -> {
                connectionTitle.text = "需要登录"
                connectionDetail.text = "DeepSeek 登录态已失效，请重新登录后继续同步。"
                statusText.text = "登录已过期"
                connectionDot.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_status_dot_warn
                )
                statusText.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            snapshot.error != null -> {
                connectionTitle.text = "连接异常"
                connectionDetail.text = "账号仍保存在本机，可点击立即同步重试。"
                statusText.text = "同步异常"
                connectionDot.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_status_dot_warn
                )
                statusText.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            else -> {
                connectionTitle.text = "已连接"
                connectionDetail.text = "本机登录态有效，数据直接从 DeepSeek 网页接口读取。"
                statusText.text = "已同步"
                connectionDot.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_status_dot_green
                )
                statusText.setTextColor(ContextCompat.getColor(this, R.color.positive))
            }
        }

        loginButton.visibility = if (!hasToken || snapshot.sessionExpired) View.VISIBLE else View.GONE
        loginButton.text = if (snapshot.sessionExpired) "重新登录 DeepSeek" else "登录 DeepSeek"
        logoutButton.visibility = if (hasToken) View.VISIBLE else View.GONE
        refreshButton.isEnabled = hasToken
        refreshButton.alpha = if (hasToken) 1f else 0.5f
    }

    private fun openLogin() {
        loginLauncher.launch(Intent(this, LoginActivity::class.java))
    }

    private fun requestRefresh() {
        if (!tokenStore.hasToken()) {
            openLogin()
            return
        }
        refreshButton.isEnabled = false
        refreshButton.text = "同步中…"
        lifecycleScope.launch {
            runCatching { UsageSyncManager.sync(this@MainActivity) }
                .onFailure { UsageSyncManager.handleSyncError(this@MainActivity, it) }
            refreshButton.text = "立即同步"
            render()
            if (usageStore.load().error != null) {
                Toast.makeText(this@MainActivity, "同步失败，请查看连接状态", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("退出登录？")
            .setMessage("本机登录态和已缓存用量将被删除，桌面小组件会显示等待登录。")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出") { _, _ ->
                tokenStore.clear()
                usageStore.clear()
                UsageSyncScheduler.cancel(this)
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                UsageWidgetUpdater.updateAll(this)
                render()
            }
            .show()
    }

    private fun requestWidgetPin(providerClass: Class<*>) {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(
                this,
                "当前启动器不支持直接添加，请长按桌面后在小组件列表中选择 DS Usage",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        manager.requestPinAppWidget(ComponentName(this, providerClass), null, null)
    }
}

