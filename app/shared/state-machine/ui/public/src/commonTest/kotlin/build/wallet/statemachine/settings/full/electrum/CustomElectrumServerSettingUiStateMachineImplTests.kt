package build.wallet.statemachine.settings.full.electrum

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.sync.ElectrumConfigServiceFake
import build.wallet.bitcoin.sync.OffElectrumServerPreferenceValueMock
import build.wallet.bitcoin.sync.OffElectrumServerWithPreviousPreferenceValueMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CustomElectrumServerSettingUiStateMachineImplTests : FunSpec({
  val defaultProps =
    CustomElectrumServerProps(
      onBack = {},
      activeNetwork = BITCOIN
    )

  val electrumConfigService = ElectrumConfigServiceFake()

  lateinit var stateMachine: CustomElectrumServerSettingUiStateMachineImpl

  beforeTest {
    stateMachine =
      CustomElectrumServerSettingUiStateMachineImpl(
        customElectrumServerUIStateMachine = object : CustomElectrumServerUiStateMachine, BodyStateMachineMock<CustomElectrumServerUiProps>("custom-server-ui-state-machine") {},
        setElectrumServerUiStateMachine =
          object : SetElectrumServerUiStateMachine,
            ScreenStateMachineMock<SetElectrumServerProps>(
              "set-electrum-server-ui-state-machine"
            ) {},
        electrumConfigService = electrumConfigService
      )
  }

  test("no initial custom server -> setting electrum server") {
    stateMachine.test(defaultProps) {
      awaitItem() // default item

      awaitScreenWithBodyModelMock<CustomElectrumServerUiProps> {
        electrumServerPreferenceValue.shouldBe(OffElectrumServerPreferenceValueMock)
        onAdjustElectrumServerClick()
      }

      // Should return null for form screen to show empty host and port text fields.
      awaitScreenWithBodyModelMock<SetElectrumServerProps> {
        currentElectrumServerDetails.shouldBeNull()
      }
    }
  }

  test("user with previous custom electrum server should have pre-populated form") {
    electrumConfigService.electrumServerPreference.value = OffElectrumServerWithPreviousPreferenceValueMock

    stateMachine.test(defaultProps) {
      awaitItem() // default item

      awaitScreenWithBodyModelMock<CustomElectrumServerUiProps> {
        electrumServerPreferenceValue.shouldBe(
          OffElectrumServerWithPreviousPreferenceValueMock
        )

        onAdjustElectrumServerClick()
      }

      // Should populate with previous
      awaitScreenWithBodyModelMock<SetElectrumServerProps> {
        currentElectrumServerDetails.shouldBe(
          OffElectrumServerWithPreviousPreferenceValueMock.previousUserDefinedElectrumServer?.electrumServerDetails
        )
      }
    }
  }
})
