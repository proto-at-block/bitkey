package build.wallet.platform

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.platform.web.BrowserNavigator

class BrowserNavigatorMock(
  turbine: (String) -> Turbine<Any>,
) : BrowserNavigator {
  val openUrlCalls = turbine("open web url calls")

  override fun open(url: String) {
    openUrlCalls += url
  }
}
