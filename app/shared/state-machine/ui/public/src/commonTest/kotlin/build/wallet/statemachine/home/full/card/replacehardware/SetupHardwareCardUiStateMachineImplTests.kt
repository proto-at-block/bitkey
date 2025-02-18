package build.wallet.statemachine.home.full.card.replacehardware

import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.None
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class SetupHardwareCardUiStateMachineImplTests : FunSpec({

  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
  val firmwareDataService = FirmwareDataServiceFake()

  val stateMachine =
    SetupHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = postSocRecTaskRepository,
      firmwareDataService = firmwareDataService
    )

  beforeTest {
    firmwareDataService.reset()
  }

  test("card is returned - Hardware Replacement") {
    val props =
      SetupHardwareCardUiProps(
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = HardwareReplacementNotification
    stateMachine.testWithVirtualTime(props) {
      // initial state is null
      awaitItem().shouldBeNull()
      // card is loaded
      awaitItem().shouldNotBeNull()
    }
  }

  test("card is returned - Pair New Hardware") {
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareUpdateState = UpToDate,
      firmwareDeviceInfo = null
    )

    val props =
      SetupHardwareCardUiProps(
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = None
    stateMachine.testWithVirtualTime(props) {
      // card is loaded
      awaitItem().shouldNotBeNull()
    }
  }

  test("card is not returned") {
    val props =
      SetupHardwareCardUiProps(
        onReplaceDevice = {}
      )
    postSocRecTaskRepository.mutableState.value = None
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeNull()
    }
  }
})
