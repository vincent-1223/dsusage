package com.dsusage.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dsusage.app.data.SecureTokenStore
import com.dsusage.app.network.SessionExpiredException
import com.dsusage.app.sync.UsageSyncManager
import com.dsusage.app.sync.UsageSyncScheduler
import com.dsusage.app.widget.UsageWidgetUpdater
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

class LoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var stateText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var validating = false
    private var destroyed = false

    private val tokenPoll = object : Runnable {
        override fun run() {
            if (destroyed) return
            checkSessionToken()
            handler.postDelayed(this, 1_500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        webView = findViewById(R.id.login_webview)
        progress = findViewById(R.id.login_progress)
        stateText = findViewById(R.id.login_state)
        findViewById<MaterialButton>(R.id.login_back_button).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        configureWebView()
        webView.loadUrl(LOGIN_URL)
        handler.post(tokenPoll)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "https" || url.scheme == "http") return false
                return runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                }.getOrDefault(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!validating) {
                    stateText.text = "登录完成后将自动连接"
                }
                checkSessionToken()
            }
        }
    }

    private fun checkSessionToken() {
        if (validating || destroyed) return
        webView.evaluateJavascript(TOKEN_SCRIPT) { raw ->
            val token = extractToken(raw)
            if (token.isNullOrBlank() || token.length < 16) return@evaluateJavascript
            validateAndSave(token)
        }
    }

    private fun validateAndSave(token: String) {
        validating = true
        stateText.text = "正在验证登录并读取用量…"
        lifecycleScope.launch {
            runCatching { UsageSyncManager.verifySession(this@LoginActivity, token) }
                .onSuccess {
                    SecureTokenStore(this@LoginActivity).save(token)
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    UsageSyncScheduler.ensurePeriodic(this@LoginActivity)
                    UsageSyncScheduler.enqueueNow(this@LoginActivity)
                    UsageWidgetUpdater.updateAll(this@LoginActivity)
                    setResult(RESULT_OK, Intent())
                    Toast.makeText(
                        this@LoginActivity,
                        "登录成功，正在同步用量",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                .onFailure { error ->
                    validating = false
                    stateText.text = when (error) {
                        is SessionExpiredException -> "还未完成登录，请继续完成 DeepSeek 验证"
                        else -> "已检测到网页登录，网络验证失败，正在重试"
                    }
                }
        }
    }

    private fun extractToken(raw: String): String? {
        if (raw.isBlank() || raw == "null") return null
        val decoded = runCatching {
            JSONTokener(raw).nextValue() as? String
        }.getOrNull() ?: return null
        if (decoded.isBlank()) return null
        return runCatching {
            val json = JSONObject(decoded)
            json.optString("value").ifBlank { json.optString("token") }
        }.getOrDefault(decoded).takeIf { it.isNotBlank() }
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val LOGIN_URL = "https://platform.deepseek.com/usage"
        const val TOKEN_SCRIPT = """
            (function() {
              try {
                var raw = localStorage.getItem('userToken');
                if (!raw) return '';
                try {
                  var parsed = JSON.parse(raw);
                  if (parsed && parsed.value) return String(parsed.value);
                  if (parsed && parsed.token) return String(parsed.token);
                  if (parsed) return '';
                } catch (ignored) {}
                return String(raw);
              } catch (error) {
                return '';
              }
            })();
        """
    }
}
