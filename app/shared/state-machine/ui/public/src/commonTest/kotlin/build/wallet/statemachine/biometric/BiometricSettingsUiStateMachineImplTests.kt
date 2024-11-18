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
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.awaitScreenWithSheetModelBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import com.github.michaelbull.result.get
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
    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitScreenWithBody<FormBodyModel> {
        biometricPreference.get()
          .get()
          .shouldNotBeNull()
          .shouldBeTrue()
      }
    }
  }

  test("unable to verify the signature of hw tap") {
    signatureVerifier.isValid = false

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      // return to the biometric settings screen
      awaitItem().bottomSheetModel.shouldBeNull()

      // show the error sheet on the biometrics screen
      awaitScreenWithSheetModelBody<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to verify your Bitkey device")
      }
    }
  }

  test("biometric hardware is not available") {
    biometricPrompter.availabilityError = BiometricError.NoHardware()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<ErrorSheetBodyModel> {
        onBack()
      }

      awaitScreenWithSheetModelBody<NotEnrolledErrorSheetBodyModel> {
        headline.shouldBe("Biometric authentication is not available on this device.")
      }
    }
  }

  test("unable to enroll from no biometric enrolled") {
    biometricPrompter.enrollError = BiometricError.NoBiometricEnrolled()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithSheetModelBody<ErrorSheetBodyModel> {
        headline.shouldBe("Unable to enable biometrics.")
      }
    }
  }

  test("unable to enroll from authentication failure") {
    biometricPrompter.enrollError = BiometricError.AuthenticationFailed()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithSheetModelBody<ErrorSheetBodyModel> {
        subline.shouldBe("We were unable to verify your biometric authentication. Please try again.")
      }
    }
  }

  test("disable biometric security authentication") {
    biometricPreference.set(true)
    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeFalse()
      }

      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        isEnabled.shouldBeTrue()
        onEnableCheckedChange(false)
      }

      awaitScreenWithSheetModelBody<NfcPromptSheetBodyModel> {
        onScanBitkeyDevice()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitScreenWithBody<FormBodyModel> {
        biometricPreference.get()
          .get()
          .shouldNotBeNull()
          .shouldBeFalse()
      }
    }
  }

  test("coachmark is hidden when switch is checked") {
    coachmarkService.defaultCoachmarks = listOf(
      CoachmarkIdentifier.BiometricUnlockCoachmark
    )

    biometricSettingsUiStateMachine.test(props) {
      // initial render, not checked
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        coachmark.shouldBeNull()
        isEnabled.shouldBeFalse()
      }

      // showing coachmark after fetch, enabling the preference
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        coachmark.shouldNotBeNull()
        isEnabled.shouldBeFalse()
        onEnableCheckedChange(true)
      }

      // coachmark is being hidden
      awaitScreenWithBody<BiometricSettingsScreenBodyModel> {
        coachmarkService.turbine.awaitItem().shouldBe(CoachmarkIdentifier.BiometricUnlockCoachmark)
      }
    }
  }
})
