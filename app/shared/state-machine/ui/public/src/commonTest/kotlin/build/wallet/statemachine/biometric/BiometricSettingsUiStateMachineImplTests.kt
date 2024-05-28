package build.wallet.statemachine.biometric

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.SignatureVerifierMock
import build.wallet.inappsecurity.BiometricPreferenceFake
import build.wallet.platform.biometrics.BiometricPrompterFake
import build.wallet.platform.biometrics.BiometricTextProviderFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class BiometricSettingsUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val onBackCalls = turbines.create<Unit>("onBack calls")

  val biometricPreference = BiometricPreferenceFake()

  val biometricSettingsUiStateMachine = BiometricSettingUiStateMachineImpl(
    biometricPreference = biometricPreference,
    biometricTextProvider = BiometricTextProviderFake(),
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    biometricPrompter = BiometricPrompterFake(),
    signatureVerifier = SignatureVerifierMock()
  )

  val props = BiometricSettingUiProps(
    keybox = KeyboxMock,
    onBack = { onBackCalls.add(Unit) }
  )

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
})

fun FormBodyModel.enableAuthSwitch(): SwitchModel {
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
