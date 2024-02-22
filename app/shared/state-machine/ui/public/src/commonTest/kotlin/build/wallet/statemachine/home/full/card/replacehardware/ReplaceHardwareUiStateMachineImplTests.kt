package build.wallet.statemachine.home.full.card.replacehardware

import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.None
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class ReplaceHardwareUiStateMachineImplTests : FunSpec({

  val props =
    ReplaceHardwareCardUiProps(
      onReplaceDevice = {}
    )

  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()

  val stateMachine =
    ReplaceHardwareCardUiStateMachineImpl(
      postSocRecTaskRepository = postSocRecTaskRepository
    )

  test("card is returned") {
    postSocRecTaskRepository.mutableState.value = HardwareReplacementNotification
    stateMachine.test(props) {
      // initial state is null
      awaitItem().shouldBeNull()
      // card is loaded
      awaitItem().shouldNotBeNull()
    }
  }

  test("card is not returned") {
    postSocRecTaskRepository.mutableState.value = None
    stateMachine.test(props) {
      awaitItem().shouldBeNull()
    }
  }
})
