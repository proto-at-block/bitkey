package build.wallet.integration.statemachine.settings.full.device.fingerprints

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.ListingFingerprintsBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetSuccessBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FinishFingerprintResetBodyModel
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.statemachine.ui.robots.clickFingerprints
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FingerprintResetFunctionalTests : FunSpec({

  lateinit var app: AppTester

  suspend fun TestScope.launchAndPrepareApp() {
    app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()
  }

  test("cancel reset from progress screen returns to security hub") {
    launchAndPrepareApp()
    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 5.seconds,
      turbineTimeout = 5.seconds
    ) {
      advanceToFingerprintResetConfirmation()

      awaitUntilBody<AppDelayNotifyInProgressBodyModel> {
        onStopRecovery.shouldNotBeNull().invoke()
      }

      awaitUntilBody<SecurityHubBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("approve reset") {
    launchAndPrepareApp()
    app.appUiStateMachine.test(
      props = Unit
    ) {
      advanceToFingerprintResetConfirmation()

      val delayNotifyInProgressBody = awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Override the D&N delay
      val fingerprintResetService = app.fingerprintResetService
      val action = fingerprintResetService.getLatestFingerprintResetAction().get().shouldNotBeNull()

      val account = app.accountService.getAccount<FullAccount>().getOrThrow()
      app.configureDelayDurationF8eClient.configureDelayDuration(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        privilegedActionId = action.id,
        delayDuration = 1.milliseconds
      ).getOrThrow()

      // Leave and re-enter fingerprint reset to pick up the change in duration
      delayNotifyInProgressBody.onExit.shouldNotBeNull().invoke()
      awaitUntilBody<SecurityHubBodyModel>().clickFingerprints()
      awaitUntilSheet<ManageFingerprintsOptionsSheetBodyModel> {
        onCannotUnlock()
      }

      // Complete the reset
      awaitUntilBody<FinishFingerprintResetBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FingerprintResetConfirmationSheetModel> {
        onConfirmReset()
      }
      // See the fingerprint instructions and tap the "Save fingerprint" button
      awaitUntilBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        primaryButton.apply {
          text.shouldBe("Save fingerprint")
          onClick.invoke()
        }
      }

      awaitUntilBody<FingerprintResetSuccessBodyModel> {
        onDone.invoke()
      }

      awaitUntilBody<ListingFingerprintsBodyModel>()
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.advanceToFingerprintResetConfirmation() {
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickFingerprints()
  awaitSheet<ManageFingerprintsOptionsSheetBodyModel> {
    onCannotUnlock()
  }
  awaitUntilBody<FingerprintResetConfirmationBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitSheet<FingerprintResetConfirmationSheetModel> {
    onConfirmReset()
  }
}
