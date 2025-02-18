@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.inappsecurity

import app.cash.turbine.test
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.inappsecurity.MoneyHomeHiddenStatus.HIDDEN
import build.wallet.inappsecurity.MoneyHomeHiddenStatus.VISIBLE
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi

class MoneyHomeHiddenStatusProviderImplTests : FunSpec({

  val appSessionManager = AppSessionManagerFake()
  val hideBalancePreference = HideBalancePreferenceFake()

  fun TestScope.provider() =
    MoneyHomeHiddenStatusProviderImpl(
      appSessionManager,
      createBackgroundScope(),
      hideBalancePreference
    )

  beforeTest {
    appSessionManager.reset()
    hideBalancePreference.reset()
  }

  test("status is visible when preference is disabled") {
    hideBalancePreference.set(false)

    val provider = provider()
    provider.hiddenStatus.test {
      awaitUntil(VISIBLE)
      awaitNoEvents()
    }
  }

  test("status changes to hidden when preference becomes enabled") {
    hideBalancePreference.set(false)

    val provider = provider()
    provider.hiddenStatus.test {
      awaitUntil(VISIBLE)

      hideBalancePreference.set(true)

      awaitItem().shouldBe(HIDDEN)
    }
  }

  test("status is hidden when preference is already enabled") {
    hideBalancePreference.set(true)

    val provider = provider()
    provider.hiddenStatus.test {
      awaitUntil(HIDDEN)
      awaitNoEvents()
    }
  }

  test("status stays visible if app goes to background while preference is disabled") {
    hideBalancePreference.set(false)

    val provider = provider()
    provider.hiddenStatus.test {
      awaitUntil(VISIBLE)

      appSessionManager.appDidEnterBackground()
      awaitNoEvents()
    }
  }

  test("status returns to hidden if preference is enabled and app is in background") {
    hideBalancePreference.set(false)

    val provider = provider()
    provider.hiddenStatus.test {
      awaitUntil(VISIBLE)

      hideBalancePreference.set(true)
      awaitItem().shouldBe(HIDDEN)

      provider.toggleStatus()
      awaitItem().shouldBe(VISIBLE)

      appSessionManager.appDidEnterBackground()
      awaitItem().shouldBe(HIDDEN)
    }
  }
})
