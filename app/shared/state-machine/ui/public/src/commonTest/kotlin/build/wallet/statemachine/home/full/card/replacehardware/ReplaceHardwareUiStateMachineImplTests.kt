package build.wallet.statemachine.home.full.card.replacehardware

import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.None
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class ReplaceHardwareUiStateMachineImplTests : FunSpec({

  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()

  val stateMachine =
    SetupHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = postSocRecTaskRepository
    )

  test("card is returned - Hardware Replacement") {
    val props =
      SetupHardwareCardUiProps(
        deviceInfo = FirmwareDeviceInfoMock,
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = HardwareReplacementNotification
    stateMachine.test(props) {
      // initial state is null
      awaitItem().shouldBeNull()
      // card is loaded
      awaitItem().shouldNotBeNull()
    }
  }

  test("card is returned - Pair New Hardware") {
    val props =
      SetupHardwareCardUiProps(
        deviceInfo = null,
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = None
    stateMachine.test(props) {
      // card is loaded
      awaitItem().shouldNotBeNull()
    }
  }

  test("card is not returned") {
    val props =
      SetupHardwareCardUiProps(
        deviceInfo = FirmwareDeviceInfoMock,
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = None
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }
})
