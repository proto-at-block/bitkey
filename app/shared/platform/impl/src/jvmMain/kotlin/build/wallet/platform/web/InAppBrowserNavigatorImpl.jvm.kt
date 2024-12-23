package build.wallet.platform.web

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug

@BitkeyInject(AppScope::class)
class InAppBrowserNavigatorImpl : InAppBrowserNavigator {
  override fun open(
    url: String,
    onClose: () -> Unit,
  ) {
    logDebug { "Opened URL: $url " }
    onClose()
  }

  override fun onClose() = Unit

  override fun close() {
    onClose()
  }
}
