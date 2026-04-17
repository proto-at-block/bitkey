package build.wallet.statemachine.biometric

import bitkey.ui.framework.test
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompterMock
import build.wallet.platform.biometrics.BiometricTextProviderFake
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.nfc.HardwarePresenceProps
import build.wallet.statemachine.nfc.HardwarePresenceUiStateMachine
import build.wallet.statemachine.ui.*
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class BiometricSettingsScreenPresenterTests : FunSpec({

  val hardwarePresenceUiStateMachine =
    object : HardwarePresenceUiStateMachine,
      ScreenStateMachineMock<HardwarePresenceProps>("hw-proof-of-possession") {}

  val biometricPreference = BiometricPreferenceFake()
  val biometricPrompter = BiometricPrompterMock()

  val biometricSettingsPresenter = BiometricSettingScreenPresenter(
    biometricPreference = biometricPreference,
    biometricTextProvider = BiometricTextProviderFake(),
    hardwarePresenceUiStateMachine = hardwarePresenceUiStateMachine,
    biometricPrompter = biometricPrompter,
    settingsLauncher = SystemSettingsLauncherMock()
  )

  val screen = BiometricSettingScreen(
    fullAccount = FullAccountMock,
    origin = null
  )

  beforeEach {
    biometricPreference.reset()
    biometricPrompter.reset()
  }

  test("enable biometric security authentication") {
    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitUntilBodyMock<HardwarePresenceProps> {
        onSuccess()
      }

      awaitUntilBody<BiometricSettingsScreenBodyModel>()

      biometricPreference.get().shouldBeOk(true)
    }
  }

  test("hardware proof of possession fails") {
    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      // go to nfc and proof of possession fails
      awaitUntilBodyMock<HardwarePresenceProps> {
        onFailure(Error("Serial number mismatch"))
      }

      // show the error sheet on the biometrics screen
      awaitUntilSheet<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to verify your Bitkey device")
      }
    }
  }

  test("biometric hardware is not available") {
    biometricPrompter.availabilityError = BiometricError.NoHardware()

    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<ErrorSheetBodyModel> {
        onBack()
      }

      awaitSheet<NotEnrolledErrorSheetBodyModel> {
        headline.shouldBe("Biometric authentication is not available on this device.")
      }
    }
  }

  test("unable to enroll from no biometric enrolled") {
    biometricPrompter.enrollError = BiometricError.NoBiometricEnrolled()

    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      // go to nfc and proof of possession succeeds
      awaitUntilBodyMock<HardwarePresenceProps> {
        onSuccess()
      }

      awaitUntilSheet<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to enable biometrics.")
      }
    }
  }

  test("unable to enroll from authentication failure") {
    biometricPrompter.enrollError = BiometricError.AuthenticationFailed()

    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      // go to nfc and proof of possession succeeds
      awaitUntilBodyMock<HardwarePresenceProps> {
        onSuccess()
      }

      awaitUntilSheet<ErrorSheetBodyModel> {
        subline.shouldBe("We were unable to verify your biometric authentication. Please try again.")
      }
    }
  }

  test("disable biometric security authentication") {
    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
      }

      biometricPreference.set(true)

      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeTrue()
        onEnableCheckedChange(false)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitUntilBodyMock<HardwarePresenceProps> {
        onSuccess()
      }

      awaitUntilBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
      }
      biometricPreference.get().shouldBeOk(false)
    }
  }
})
