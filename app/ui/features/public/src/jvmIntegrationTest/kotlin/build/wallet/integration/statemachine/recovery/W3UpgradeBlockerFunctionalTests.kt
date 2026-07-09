package build.wallet.integration.statemachine.recovery

import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class W3UpgradeBlockerFunctionalTests : FunSpec({

  test("W3 upgrade blocker is shown to legacy hardware accounts and is dismissible") {
    val app = launchNewApp()
    // Override the flag locally so this test doesn't depend on the remote flag value.
    app.w3UpgradeBlockerFeatureFlag.setFlagValue(BooleanFlag(true), overridden = true)
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)
    // The blocker is only shown 14+ days after onboarding completes; record a backdated
    // completion timestamp to make this account eligible.
    app.onboardingCompletionDao.recordCompletion(timestamp = Clock.System.now() - 15.days)
      .getOrThrow()

    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<W3UpgradeBlockerBodyModel>()
        .onClose()
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
