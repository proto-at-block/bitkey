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
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
        enableAuthSwitch().onCheckedChange(true)
      }

      awaitItem().bottomSheetModel
        .shouldNotBeNull()
        .body
        .shouldBeInstanceOf<FormBodyModel>()
        .primaryButton
        .shouldNotBeNull()
        .onClick()

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
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
        enableAuthSwitch().onCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .onClick()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      // return to the biometric settings screen
      awaitItem().bottomSheetModel.shouldBeNull()

      // show the error sheet on the biometrics screen
      awaitScreenWithSheetModelBody<FormBodyModel> {
        header
          .shouldNotBeNull()
          .headline
          .shouldBe("Unable to verify your Bitkey device")
      }
    }
  }

  test("biometric hardware is not available") {
    biometricPrompter.availabilityError = BiometricError.NoHardware()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
        enableAuthSwitch().onCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .onClick()
      }

      awaitScreenWithSheetModelBody<FormBodyModel> {
        header
          .shouldNotBeNull()
          .headline
          .shouldBe("Biometric authentication is not available on this device.")
      }
    }
  }

  test("unable to enroll from no biometric enrolled") {
    biometricPrompter.enrollError = BiometricError.NoBiometricEnrolled()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
        enableAuthSwitch().onCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .onClick()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithSheetModelBody<FormBodyModel> {
        header
          .shouldNotBeNull()
          .headline
          .shouldBe("Unable to enable biometrics.")
      }
    }
  }

  test("unable to enroll from authentication failure") {
    biometricPrompter.enrollError = BiometricError.AuthenticationFailed()

    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
        enableAuthSwitch().onCheckedChange(true)
      }

      awaitScreenWithSheetModelBody<FormBodyModel> {
        primaryButton
          .shouldNotBeNull()
          .onClick()
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      // go to nfc and successfully scan the hw
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<String>> {
        onSuccess("success")
      }

      awaitItem().bottomSheetModel.shouldBeNull()

      awaitScreenWithSheetModelBody<FormBodyModel> {
        header
          .shouldNotBeNull()
          .sublineModel
          .shouldNotBeNull()
          .string
          .shouldBe("We were unable to verify your biometric authentication. Please try again.")
      }
    }
  }

  test("disable biometric security authentication") {
    biometricPreference.set(true)
    biometricSettingsUiStateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
      }

      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeTrue()
        enableAuthSwitch().onCheckedChange(false)
      }

      awaitItem().bottomSheetModel
        .shouldNotBeNull()
        .body
        .shouldBeInstanceOf<FormBodyModel>()
        .primaryButton
        .shouldNotBeNull()
        .onClick()

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
      awaitScreenWithBody<FormBodyModel> {
        enableAuthSwitch().checked.shouldBeFalse()
      }

      // showing coachmark after fetch, enabling the preference
      awaitScreenWithBody<FormBodyModel> {
        enableAuthCoachmark().shouldNotBeNull()
        enableAuthSwitch().onCheckedChange(true)
      }

      // coachmark is being hidden
      awaitScreenWithBody<FormBodyModel> {
        coachmarkService.turbine.awaitItem().shouldBe(CoachmarkIdentifier.BiometricUnlockCoachmark)
      }

      // coachmark is now hidden and marked
      awaitScreenWithBody<FormBodyModel>()
    }
  }
})

private fun FormBodyModel.enableAuthSwitch(): SwitchModel {
  return mainContentList[0]
    .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
    .listGroupModel
    .items[0]
    .shouldBeInstanceOf<ListItemModel>()
    .trailingAccessory
    .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>()
    .model
    .shouldBeInstanceOf<SwitchModel>()
}

fun FormBodyModel.enableAuthCoachmark(): CoachmarkModel? {
  return mainContentList[0]
    .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
    .listGroupModel
    .items[0]
    .shouldBeInstanceOf<ListItemModel>()
    .coachmark
}
