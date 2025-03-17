package build.wallet.ui.components.webview

import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun WebView(url: String) {
  AndroidView(
    factory = { context ->
      android.webkit.WebView(context).apply {
        webViewClient = WebViewClient()
        settings.javaScriptEnabled = true
        loadUrl(url)
      }
    }
  )
}
