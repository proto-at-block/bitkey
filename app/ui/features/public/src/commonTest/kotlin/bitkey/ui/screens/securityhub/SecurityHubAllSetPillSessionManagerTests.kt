package bitkey.ui.screens.securityhub

import build.wallet.coroutines.createBackgroundScope
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class SecurityHubAllSetPillSessionManagerTests : FunSpec({
  test("resets auto-hidden state when the app backgrounds") {
    val appSessionManager = AppSessionManagerFake()
    val sessionManager = SecurityHubAllSetPillSessionManagerImpl(
      appSessionManager = appSessionManager,
      appScope = createBackgroundScope()
    )

    sessionManager.hasAutoHiddenPillInCurrentForegroundSession.value.shouldBeFalse()

    sessionManager.markPillAutoHidden(sessionManager.foregroundSessionGeneration.value).shouldBe(true)
    sessionManager.hasAutoHiddenPillInCurrentForegroundSession.value.shouldBeTrue()

    appSessionManager.appDidEnterBackground()

    sessionManager.hasAutoHiddenPillInCurrentForegroundSession.value.shouldBeFalse()
  }

  test("ignores stale auto-hide completion after the app backgrounds") {
    val appSessionManager = AppSessionManagerFake()
    val sessionManager = SecurityHubAllSetPillSessionManagerImpl(
      appSessionManager = appSessionManager,
      appScope = createBackgroundScope()
    )
    val originalGeneration = sessionManager.foregroundSessionGeneration.value

    appSessionManager.appDidEnterBackground()
    sessionManager.markPillAutoHidden(originalGeneration).shouldBe(false)

    sessionManager.hasAutoHiddenPillInCurrentForegroundSession.value.shouldBeFalse()
  }

  test("tracks whether the app is foregrounded") {
    val appSessionManager = AppSessionManagerFake()
    val sessionManager = SecurityHubAllSetPillSessionManagerImpl(
      appSessionManager = appSessionManager,
      appScope = createBackgroundScope()
    )

    sessionManager.isAppForegrounded.value.shouldBeTrue()

    appSessionManager.appDidEnterBackground()
    sessionManager.isAppForegrounded.value.shouldBeFalse()

    appSessionManager.appDidEnterForeground()
    sessionManager.isAppForegrounded.value.shouldBeTrue()
  }
})
