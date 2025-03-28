package build.wallet.statemachine.biometric

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompterMock
import build.wallet.platform.biometrics.BiometricTextProviderFake
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BiometricSettingsUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val onBackCalls = turbines.create<Unit>("onBack calls")

  val biometricPreference = BiometricPreferenceFake()
  val signatureVerifier = SignatureVerifierMock()
  val biometricPrompter = BiometricPrompterMock()
  val coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create)

  val biometricSettingsUiStateMachine = BiometricSettingUiStateMachineImpl(
    biometricPreference = biometricPreference,
    biometricTextProvider = BiometricTextProviderFake(),
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    biometricPrompter = biometricPrompter,
    signatureVerifier = signatureVerifier,
    settingsLauncher = SystemSettingsLauncherMock(),
    coachmarkService = coachmarkService
  )

  val props = BiometricSettingUiProps(
    keybox = KeyboxMock,
    onBack = { onBackCalls.add(Unit) }
  )

  beforeEach {
    signatureVerifier.reset()
    biometricPreference.reset()
    biometricPrompter.reset()
    coachmarkService.resetCoachmarks()
  }

  test("enable biometric security authentication") {
    biometricSettingsUiStateMachine.testWithVirtualTime(props) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitBody<BiometricSettingsScreenBodyModel>()

      biometricPreference.get().shouldBeOk(true)
    }
  }

  test("unable to verify the signature of hw tap") {
    signatureVerifier.isValid = false

    biometricSettingsUiStateMachine.testWithVirtualTime(props) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      // return to the biometric settings screen
      awaitItem().bottomSheetModel.shouldBeNull()

      // show the error sheet on the biometrics screen
      awaitSheet<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to verify your Bitkey device")
      }
    }
  }

  test("biometric hardware is not available") {
    biometricPrompter.availabilityError = BiometricError.NoHardware()

    biometricSettingsUiStateMachine.test(props) {
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

    biometricSettingsUiStateMachine.testWithVirtualTime(props) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitSheet<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to enable biometrics.")
      }
    }
  }

  test("unable to enroll from authentication failure") {
    biometricPrompter.enrollError = BiometricError.AuthenticationFailed()

    biometricSettingsUiStateMachine.testWithVirtualTime(props) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitSheet<ErrorSheetBodyModel> {
        subline.shouldBe("We were unable to verify your biometric authentication. Please try again.")
      }
    }
  }

  test("disable biometric security authentication") {
    biometricPreference.set(true)
    biometricSettingsUiStateMachine.testWithVirtualTime(props) {
      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
      }

      awaitBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeTrue()
        onEnableCheckedChange(false)
      }

      awaitSheet<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitBodyMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitBody<BiometricSettingsScreenBodyModel>()
      biometricPreference.get().shouldBeOk(false)
    }
  }

  test("coachmark is hidden when switch is checked") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.BiometricUnlockCoachmark
    )

    biometricSettingsUiStateMachine.test(props) {
      // initial render, not checked
      awaitBody<BiometricSettingsScreenBodyModel> {
        coachmark.shouldBeNull()
        isEnabled.shouldBeFalse()
      }

      // showing coachmark after fetch, enabling the preference
      awaitBody<BiometricSettingsScreenBodyModel> {
        coachmark.shouldNotBeNull()
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      // coachmark is being hidden
      awaitBody<BiometricSettingsScreenBodyModel> {
        coachmarkService.markDisplayedTurbine.awaitItem()
          .shouldBe(CoachmarkIdentifier.BiometricUnlockCoachmark)
      }
    }
  }
})
