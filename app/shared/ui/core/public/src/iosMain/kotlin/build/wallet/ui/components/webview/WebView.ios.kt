package build.wallet.ui.components.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WebView(url: String) {
  UIKitView(
    modifier = Modifier,
    factory = {
      WKWebView(
        frame = cValue(),
        configuration = WKWebViewConfiguration()
      )
    },
    update = { webView ->
      webView.loadRequest(NSURLRequest(NSURL(string = url)))
    }
  )
}
