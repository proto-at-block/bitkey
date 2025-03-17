package build.wallet.ui.components.webview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WebView(url: String) {
  val factory = remember {
    {
      WKWebView(
        frame = cValue { CGRectZero },
        configuration = WKWebViewConfiguration()
      ).apply {
        loadRequest(NSURLRequest(NSURL(string = url)))
      }
    }
  }
  UIKitView(
    factory = factory,
    modifier = Modifier.fillMaxSize(),
    properties = UIKitInteropProperties(
      isInteractive = true,
      isNativeAccessibilityEnabled = true
    )
  )
}
