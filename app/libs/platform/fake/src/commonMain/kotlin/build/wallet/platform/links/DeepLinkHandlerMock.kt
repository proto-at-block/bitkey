package build.wallet.platform.links

import app.cash.turbine.Turbine
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Success
import build.wallet.platform.links.OpenDeeplinkResult.Opened

class DeepLinkHandlerMock(
  turbine: (String) -> Turbine<String>,
) : DeepLinkHandler {
  var openDeeplinkResult: OpenDeeplinkResult = Opened(
    appRestrictionResult = Success
  )

  val openDeeplinkCalls = turbine.invoke("openDeeplink calls")

  override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult {
    openDeeplinkCalls.add(url)
    return openDeeplinkResult
  }

  fun reset() {
    openDeeplinkResult = Opened(
      appRestrictionResult = Success
    )
  }
}
