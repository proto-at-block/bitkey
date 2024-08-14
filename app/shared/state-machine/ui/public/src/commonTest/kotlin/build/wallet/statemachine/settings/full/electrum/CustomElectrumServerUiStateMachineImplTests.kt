package build.wallet.statemachine.settings.full.electrum

import build.wallet.bitcoin.sync.ElectrumConfigServiceFake
import build.wallet.bitcoin.sync.OffElectrumServerPreferenceValueMock
import build.wallet.bitcoin.sync.OffElectrumServerWithPreviousPreferenceValueMock
import build.wallet.bitcoin.sync.OnElectrumServerPreferenceValueMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CustomElectrumServerUiStateMachineImplTests : FunSpec({

  val onSetElectrumServerCalls = turbines.create<Unit>("set electrum server click calls")

  val props =
    CustomElectrumServerUiProps(
      onBack = {},
      electrumServerPreferenceValue = OffElectrumServerPreferenceValueMock,
      onAdjustElectrumServerClick = {
        onSetElectrumServerCalls.add(Unit)
      }
    )
  val electrumConfigService = ElectrumConfigServiceFake()

  lateinit var stateMachine: CustomElectrumServerUiStateMachineImpl

  beforeTest {
    stateMachine =
      CustomElectrumServerUiStateMachineImpl(
        electrumConfigService = electrumConfigService
      )

    electrumConfigService.reset()
  }

  test("initial state - without custom electrum server") {
    stateMachine.test(props) {
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }

  test("enable without custom electrum server -> set electrum server") {
    stateMachine.test(props) {
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      onSetElectrumServerCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("enabled custom electrum server -> disable custom electrum server") {
    stateMachine.test(
      props.copy(electrumServerPreferenceValue = OnElectrumServerPreferenceValueMock)
    ) {
      electrumConfigService.electrumServerPreference.value = OnElectrumServerPreferenceValueMock

      // Showing custom electrum server, hydrated from Settings state machine
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        with(switchCardModel.actionRows.first()) {
          title.shouldBe("Connected to:")
          sideText.shouldBe("ssl://chicken.info:50002")
        }

        switchCardModel.switchModel.onCheckedChange(true)
      }

      // Show confirmation alert
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldNotBeNull().onPrimaryButtonClick()

        // Should have reset the ElectrumServer to default
        electrumConfigService.electrumServerPreference().value.shouldBe(
          OffElectrumServerWithPreviousPreferenceValueMock
        )
      }

      // Verify that the confirmation dialog has been dismissed
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }

      // Then switch should be off.
      awaitBody<CustomElectrumServerBodyModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }
})
