package build.wallet.statemachine.moneyhome.card.replacehardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.None
import build.wallet.statemachine.moneyhome.card.CardModel

class ReplaceHardwareCardUiStateMachineImpl(
  val postSocRecTaskRepository: PostSocRecTaskRepository,
) : ReplaceHardwareCardUiStateMachine {
  @Composable
  override fun model(props: ReplaceHardwareCardUiProps): CardModel? {
    val recoveryIncomplete = postSocRecTaskRepository.taskState.collectAsState(None)

    return when (recoveryIncomplete.value) {
      HardwareReplacementScreens, HardwareReplacementNotification ->
        ReplaceHardwareCardModel(
          onReplaceDevice = {
            props.onReplaceDevice()
          }
        )
      None -> null
    }
  }
}
