package build.wallet.ui.components.webview

import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import build.wallet.statemachine.core.form.FormMainContentModel.WebView

@Composable
fun WebView(model: WebView) {
  AndroidView(
    factory = { context ->
      android.webkit.WebView(context).apply {
        webViewClient = WebViewClient()
        settings.javaScriptEnabled = true
        loadUrl(model.url)
      }
    }
  )
}
