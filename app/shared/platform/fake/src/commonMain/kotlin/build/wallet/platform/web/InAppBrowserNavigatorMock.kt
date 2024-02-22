package build.wallet.platform.web

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

class InAppBrowserNavigatorMock(
  val turbine: (String) -> Turbine<Any>,
) : InAppBrowserNavigator {
  val onOpenCalls = turbine("open-calls")
  val onCloseCalls = turbine("close-calls")

  var onCloseCallback: (() -> Unit)? = null

  override fun open(
    url: String,
    onClose: () -> Unit,
  ) {
    onCloseCallback = onClose
    onOpenCalls += url
  }

  override fun onClose() {
    onCloseCalls += Unit
  }

  fun reset() {
    onCloseCallback = null
  }
}
