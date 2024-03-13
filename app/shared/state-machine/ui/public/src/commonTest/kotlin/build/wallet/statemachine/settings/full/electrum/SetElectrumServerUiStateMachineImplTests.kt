package build.wallet.statemachine.settings.full.electrum

import build.wallet.analytics.events.screen.id.CustomElectrumServerEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.IncompatibleNetwork
import build.wallet.bitcoin.sync.ElectrumReachability.ElectrumReachabilityError.Unreachable
import build.wallet.bitcoin.sync.ElectrumReachabilityMock
import build.wallet.bitcoin.sync.ElectrumServerSettingProviderMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.input.onValueChange
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf

class SetElectrumServerUiStateMachineImplTests : FunSpec({

  val onSetServerCalls = turbines.create<Unit>("saved electrum server")
  val props =
    SetElectrumServerProps(
      onClose = {},
      currentElectrumServerDetails = null,
      activeNetwork = BITCOIN,
      onSetServer = {
        onSetServerCalls.add(Unit)
      }
    )

  val electrumPreferenceMock = ElectrumServerSettingProviderMock(turbine = turbines::create)
  val reachabilityMock = ElectrumReachabilityMock(Ok(Unit))
  val unreachabilityMock = ElectrumReachabilityMock(Err(Unreachable(BdkError.Generic(null, null))))
  val delayer = ControlledDelayer()

  lateinit var stateMachine: SetElectrumServerUiStateMachineImpl

  beforeTest {
    delayer.reset()
    stateMachine =
      SetElectrumServerUiStateMachineImpl(
        delayer = delayer,
        electrumServerSettingProvider = electrumPreferenceMock,
        electrumReachability = reachabilityMock
      )
  }

  test("save reachable electrum server") {
    stateMachine.test(props) {
      val newHost = "chicken.info"
      val newPort = "6789"

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newHost)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newHost)
        }

        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newPort)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newPort)
          clickPrimaryButton()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        // Should have saved electrum preference
        electrumPreferenceMock.setCalls.awaitItem()
        onSetServerCalls.awaitItem().shouldBe(Unit)
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("do not save unreachable electrum server, show error screen") {
    stateMachine =
      SetElectrumServerUiStateMachineImpl(
        delayer = delayer,
        electrumServerSettingProvider = electrumPreferenceMock,
        electrumReachability = unreachabilityMock
      )

    stateMachine.test(props) {
      val newHost = "chicken.info"
      val newPort = "6789"

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newHost)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newHost)
        }

        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newPort)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newPort)
          clickPrimaryButton()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Unable to contact Electrum server")
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_ERROR)
        clickPrimaryButton()
      }

      awaitScreenWithBody<FormBodyModel> {
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE)
      }
    }
  }

  test("if Electrum server is incompatible, show error screen") {
    stateMachine =
      SetElectrumServerUiStateMachineImpl(
        delayer = delayer,
        electrumServerSettingProvider = electrumPreferenceMock,
        electrumReachability = ElectrumReachabilityMock(Err(IncompatibleNetwork))
      )

    stateMachine.test(props) {
      val newHost = "chicken.info"
      val newPort = "6789"

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newHost)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newHost)
        }

        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBeEmpty()
          primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
          fieldModel.onValueChange(newPort)
        }
      }

      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList[1].shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(newPort)
          clickPrimaryButton()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Incompatible Electrum server")
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE_ERROR)
        clickPrimaryButton()
      }

      awaitScreenWithBody<FormBodyModel> {
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(CustomElectrumServerEventTrackerScreenId.CUSTOM_ELECTRUM_SERVER_UPDATE)
      }
    }
  }
})
