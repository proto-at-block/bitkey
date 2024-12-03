package build.wallet.inappsecurity

import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyHomeHiddenStatusProviderImplTests : FunSpec({
  coroutineTestScope = true

  val appSessionManager = AppSessionManagerFake()
  val hideBalancePreference = HideBalancePreferenceFake()

  // the internal flow doesn't respond to the background events
  xtest("Status returns to hidden if preference is enabled and app is in background") {
    hideBalancePreference.set(true)

    val provider = MoneyHomeHiddenStatusProviderImpl(
      appSessionManager,
      backgroundScope,
      hideBalancePreference
    )

    provider.hiddenStatus.value.shouldBe(MoneyHomeHiddenStatus.HIDDEN)

    provider.toggleStatus()

    provider.hiddenStatus.value.shouldBe(MoneyHomeHiddenStatus.VISIBLE)

    appSessionManager.appDidEnterBackground()
    provider.hiddenStatus.value.shouldBe(MoneyHomeHiddenStatus.HIDDEN)
  }
})
