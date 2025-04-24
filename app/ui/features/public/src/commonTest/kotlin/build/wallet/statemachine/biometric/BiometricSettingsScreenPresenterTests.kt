package build.wallet.statemachine.biometric

import bitkey.ui.framework.test
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompterMock
import build.wallet.platform.biometrics.BiometricTextProviderFake
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.*
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class BiometricSettingsScreenPresenterTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val biometricPreference = BiometricPreferenceFake()
  val signatureVerifier = SignatureVerifierMock()
  val biometricPrompter = BiometricPrompterMock()

  val biometricSettingsPresenter = BiometricSettingScreenPresenter(
    biometricPreference = biometricPreference,
    biometricTextProvider = BiometricTextProviderFake(),
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    biometricPrompter = biometricPrompter,
    signatureVerifier = signatureVerifier,
    settingsLauncher = SystemSettingsLauncherMock()
  )

  val screen = BiometricSettingScreen(
    fullAccount = FullAccountMock,
    origin = null
  )

  beforeEach {
    signatureVerifier.reset()
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

      awaitUntilBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitUntilBody<BiometricSettingsScreenBodyModel>()

      biometricPreference.get().shouldBeOk(true)
    }
  }

  test("unable to verify the signature of hw tap") {
    signatureVerifier.isValid = false

    biometricSettingsPresenter.test(screen) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      // go to nfc and successfully scan the hw
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
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

      // go to nfc and successfully scan the hw
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
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

      // go to nfc and successfully scan the hw
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
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

      awaitUntilBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitUntilBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
      }
      biometricPreference.get().shouldBeOk(false)
    }
  }
})
