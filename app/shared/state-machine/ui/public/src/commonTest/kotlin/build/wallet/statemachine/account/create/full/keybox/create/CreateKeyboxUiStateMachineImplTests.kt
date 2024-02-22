package build.wallet.statemachine.account.create.full.keybox.create

import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData
import io.kotest.core.spec.style.FunSpec

class CreateKeyboxUiStateMachineImplTests : FunSpec({

  val stateMachine =
    CreateKeyboxUiStateMachineImpl(
      pairNewHardwareUiStateMachine =
        object : PairNewHardwareUiStateMachine, ScreenStateMachineMock<PairNewHardwareProps>(
          id = "hw-onboard"
        ) {}
    )

  val props =
    CreateKeyboxUiProps(
      createKeyboxData =
        CreateKeyboxData.CreatingAppKeysData(
          keyboxConfig = KeyboxConfigMock,
          rollback = {}
        ),
      isHardwareFake = true
    )

  test("initial state") {
    stateMachine.test(
      props.copy(
        createKeyboxData =
          CreateKeyboxData.CreatingAppKeysData(
            keyboxConfig = KeyboxConfigMock,
            rollback = {}
          )
      )
    ) {
      // Pair hw screen
      awaitScreenWithBodyModelMock<PairNewHardwareProps>()
    }
  }

  test("happy path") {
    stateMachine.test(props) {
      // Loading key cross draft
      awaitScreenWithBodyModelMock<PairNewHardwareProps>()

      updateProps(
        props.copy(
          createKeyboxData =
            CreateKeyboxData.HasAppKeysData(
              rollback = {},
              keyboxConfig = KeyboxConfigMock,
              onPairHardwareComplete = {}
            )
        )
      )

      // Pairing with HW, key cross draft loaded
      awaitScreenWithBodyModelMock<PairNewHardwareProps>("hw-onboard")

      updateProps(
        props.copy(
          createKeyboxData =
            CreateKeyboxData.HasAppAndHardwareKeysData(
              rollback = {}
            )
        )
      )

      // Pairing with Server
      awaitScreenWithBody<LoadingBodyModel>()

      updateProps(props.copy(createKeyboxData = CreateKeyboxData.PairingWithServerData))
    }
  }
})
