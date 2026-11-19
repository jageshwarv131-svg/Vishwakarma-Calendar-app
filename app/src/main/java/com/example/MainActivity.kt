package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.example.ui.theme.MyApplicationTheme
import java.io.ByteArrayInputStream
import java.io.IOException

private const val APP_ASSETS_HOST = "appassets.androidplatform.net"
private const val ENTRY_URL = "https://$APP_ASSETS_HOST/assets/index.html"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        PanchangAppScreen()
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PanchangAppScreen() {
  val context = LocalContext.current
  val view = LocalView.current
  val isDark = isSystemInDarkTheme()
  val bgColor = if (isDark) Color(0xFF1B1A17) else Color(0xFFE9E2D0)
  val webBgInt = if (isDark) AndroidColor.rgb(27, 26, 23) else AndroidColor.rgb(233, 226, 208)

  SideEffect {
    val window = (view.context as? Activity)?.window ?: return@SideEffect
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !isDark
    controller.isAppearanceLightNavigationBars = !isDark
  }

  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.apply {
        stopLoading()
        destroy()
      }
      webViewRef = null
    }
  }

  BackHandler {
    val webView = webViewRef
    if (webView == null) {
      (context as? Activity)?.finish()
      return@BackHandler
    }
    webView.evaluateJavascript(
      """
      (function() {
        var sheet = document.getElementById('sheet');
        if (sheet && sheet.classList.contains('on')) {
          sheet.classList.remove('on');
          return 'handled';
        }
        var calView = document.getElementById('v-cal');
        if (calView && !calView.classList.contains('on') && typeof go === 'function') {
          go('cal');
          return 'handled';
        }
        return 'exit';
      })();
      """.trimIndent()
    ) { result ->
      if (result != "\"handled\"") {
        (context as? Activity)?.finish()
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(bgColor)
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .testTag("panchang_webview"),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          overScrollMode = View.OVER_SCROLL_NEVER
          isVerticalScrollBarEnabled = false
          isHorizontalScrollBarEnabled = false
          setBackgroundColor(webBgInt)

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              isAlgorithmicDarkeningAllowed = false
            }
          }

          webChromeClient = WebChromeClient()
          webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
              view: WebView,
              request: WebResourceRequest
            ): WebResourceResponse? {
              val uri = request.url
              if (uri.scheme == "https" && uri.host == APP_ASSETS_HOST) {
                val path = uri.path.orEmpty()
                if (path.startsWith("/assets/")) {
                  val assetPath = path.removePrefix("/assets/")
                  return try {
                    val input = ctx.assets.open(assetPath)
                    val mime = when {
                      assetPath.endsWith(".html", ignoreCase = true) -> "text/html"
                      assetPath.endsWith(".js", ignoreCase = true) -> "application/javascript"
                      assetPath.endsWith(".css", ignoreCase = true) -> "text/css"
                      assetPath.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                      assetPath.endsWith(".png", ignoreCase = true) -> "image/png"
                      assetPath.endsWith(".json", ignoreCase = true) -> "application/json"
                      else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, "UTF-8", input)
                  } catch (_: IOException) {
                    WebResourceResponse(
                      "text/plain",
                      "UTF-8",
                      404,
                      "Not Found",
                      emptyMap(),
                      ByteArrayInputStream(ByteArray(0))
                    )
                  }
                }
              }
              return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
              view: WebView,
              request: WebResourceRequest
            ): Boolean {
              val uri = request.url
              if (uri.scheme == "https" && uri.host == APP_ASSETS_HOST) {
                return false
              }
              return try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
              } catch (_: ActivityNotFoundException) {
                true
              }
            }
          }

          webViewRef = this
          loadUrl(ENTRY_URL)
        }
      },
      update = { webView ->
        webView.setBackgroundColor(webBgInt)
      }
    )
  }
}
