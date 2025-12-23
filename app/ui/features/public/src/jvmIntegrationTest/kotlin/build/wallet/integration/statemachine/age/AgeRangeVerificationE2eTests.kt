package build.wallet.integration.statemachine.age

import build.wallet.availability.AgeRangeVerificationResult
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class AgeRangeVerificationE2eTests : FunSpec({

  test("shows splash screen then age restricted screen when minor detected") {
    val app = launchNewApp()

    // Enable age verification feature flag and set denied result
    app.ageRangeVerificationFeatureFlag.setFlagValue(true)
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Denied

    app.appUiStateMachine.test(Unit, turbineTimeout = 10.seconds) {
      // Should show splash screen first
      awaitUntilBody<SplashBodyModel>()

      // Then age restricted screen
      awaitUntilBody<AgeRestrictedBodyModel> {
        header?.headline shouldBe "Access denied"
      }
    }
  }

  test("shows getting started screen when adult verified") {
    val app = launchNewApp()

    // Enable age verification feature flag
    app.ageRangeVerificationFeatureFlag.setFlagValue(true)
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Allowed

    app.appUiStateMachine.test(Unit, turbineTimeout = 10.seconds) {
      // Should show splash screen first
      awaitUntilBody<SplashBodyModel>()

      // Then getting started screen
      awaitUntilBody<ChooseAccountAccessModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("shows getting started screen when feature flag disabled regardless of age result") {
    val app = launchNewApp()

    // Disable age verification feature flag - result should be ignored
    app.ageRangeVerificationFeatureFlag.setFlagValue(false)
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Denied

    app.appUiStateMachine.test(Unit, turbineTimeout = 10.seconds) {
      // Should show splash screen first
      awaitUntilBody<SplashBodyModel>()

      // Then getting started screen (not age restricted, because flag is disabled)
      awaitUntilBody<ChooseAccountAccessModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("age range verification does not block money home for existing accounts") {
    val app = launchNewApp()

    // First, onboard an account normally (with age verification allowed)
    app.ageRangeVerificationFeatureFlag.setFlagValue(true)
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Allowed

    app.onboardFullAccountWithFakeHardware()

    // Now set denied result - should not affect existing account
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Denied

    app.appUiStateMachine.test(Unit, turbineTimeout = 10.seconds) {
      // Should still reach money home, not age restricted screen
      awaitUntilBody<MoneyHomeBodyModel>()

      // Verify subsequent events are still money home, not age restricted
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("age range verification does not reappear after app relaunch for existing accounts") {
    val app = launchNewApp()

    // Onboard with age verification
    app.ageRangeVerificationFeatureFlag.setFlagValue(true)
    app.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Allowed

    app.onboardFullAccountWithFakeHardware()

    // Relaunch app with denied age result
    val relaunchedApp = app.relaunchApp()
    relaunchedApp.ageRangeVerificationServiceImpl.fakeResult = AgeRangeVerificationResult.Denied

    relaunchedApp.appUiStateMachine.test(Unit, turbineTimeout = 10.seconds) {
      // Should still reach money home after relaunch
      awaitUntilBody<MoneyHomeBodyModel>()

      // Verify subsequent events are still money home, not age restricted
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }
})
