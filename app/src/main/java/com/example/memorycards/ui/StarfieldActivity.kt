package com.example.memorycards.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject

class StarfieldActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private lateinit var dbHelper: DatabaseHelper

    companion object {
        const val STARFIELD_URL = "https://df.tail58cde9.ts.net/starfield/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starfield)

        dbHelper = DatabaseHelper(this)

        webView = findViewById(R.id.starfieldWebView)
        loadingOverlay = findViewById(R.id.starfieldLoading)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
        }

        webView.clearCache(true)

        webView.webChromeClient = object : WebChromeClient() {}

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingOverlay.visibility = View.GONE
                injectCards()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                loadingOverlay.visibility = View.GONE
            }
        }

        webView.addJavascriptInterface(StarfieldBridge(), "MemoryCards")
        webView.loadUrl(STARFIELD_URL)
    }

    private fun injectCards() {
        val cards = dbHelper.getAllCards()
        val jsonCards = JSONArray()
        cards.forEach { card ->
            jsonCards.put(JSONObject().apply {
                put("id", card.id)
                put("front", card.front)
                put("back", card.back)
                put("box", card.box)
                put("categoryPath", card.categoryPath)
            })
        }
        val escaped = jsonCards.toString().replace("\\", "\\\\").replace("'", "\\'")
        webView.evaluateJavascript(
            "javascript:(function(){if(window.__starfieldCardsInject)return;window.__starfieldCardsInject=true;var cards=$escaped;if(window.loadCardsFromAndroid)window.loadCardsFromAndroid(cards);})()",
            null
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    inner class StarfieldBridge {
        @JavascriptInterface
        fun getCards(): String {
            val cards = dbHelper.getAllCards()
            val jsonCards = JSONArray()
            cards.forEach { card ->
                jsonCards.put(JSONObject().apply {
                    put("id", card.id)
                    put("front", card.front)
                    put("back", card.back)
                    put("box", card.box)
                    put("categoryPath", card.categoryPath)
                })
            }
            return jsonCards.toString()
        }

        @JavascriptInterface
        fun onReview(cardId: Long, remembered: Boolean) {
            dbHelper.updateCardReview(cardId, remembered)
        }

        @JavascriptInterface
        fun getCardsSync(): String {
            return getCards()
        }

        /** 星空页右上角「退出星空」→ 关闭本页回到 App */
        @JavascriptInterface
        fun exitStarfield() {
            runOnUiThread { finish() }
        }
    }
}