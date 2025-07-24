package build.wallet.integration.statemachine.settings.full.device.fingerprints

import app.cash.turbine.ReceiveTurbine
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationSheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FinishFingerprintResetBodyModel
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class FingerprintResetFunctionalTests : FunSpec({

  lateinit var app: AppTester

  suspend fun TestScope.launchAndPrepareApp() {
    app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()
  }

  test("fingerprint reset flow") {
    launchAndPrepareApp()
    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 5.seconds,
      turbineTimeout = 5.seconds
    ) {
      advanceToFingerprintResetConfirmation()

      awaitUntilBody<AppDelayNotifyInProgressBodyModel> {
        onExit.shouldNotBeNull().invoke()
      }

      awaitUntilBody<DeviceSettingsFormBodyModel>()
    }
  }

  test("cancel reset from progress screen returns to device settings") {
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

      awaitUntilBody<DeviceSettingsFormBodyModel>()
    }
  }

  xtest("approve reset") {
    launchAndPrepareApp()
    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 65.seconds,
      turbineTimeout = 65.seconds
    ) {
      advanceToFingerprintResetConfirmation()

      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // TODO: make FP reset D+N time configurable
      delay(61.seconds)

      awaitUntilBody<FinishFingerprintResetBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<DeviceSettingsFormBodyModel>()
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.advanceToFingerprintResetConfirmation() {
  awaitUntilBody<MoneyHomeBodyModel>()
    .clickSettings()
  awaitUntilBody<SettingsBodyModel>()
    .clickBitkeyDevice()
  awaitUntilBody<DeviceSettingsFormBodyModel>()
    .onManageFingerprints()
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
