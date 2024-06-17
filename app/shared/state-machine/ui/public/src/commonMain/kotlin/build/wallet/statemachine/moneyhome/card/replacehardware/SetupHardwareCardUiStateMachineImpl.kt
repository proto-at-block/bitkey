package build.wallet.statemachine.moneyhome.card.replacehardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementNotification
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.HardwareReplacementScreens
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.None
import build.wallet.statemachine.moneyhome.card.CardModel

class SetupHardwareCardUiStateMachineImpl(
  val postSocRecTaskRepository: PostSocRecTaskRepository,
) : SetupHardwareCardUiStateMachine {
  @Composable
  override fun model(props: SetupHardwareCardUiProps): CardModel? {
    val recoveryIncomplete = postSocRecTaskRepository.taskState.collectAsState(None)

    return when {
      recoveryIncomplete.value in setOf(HardwareReplacementScreens, HardwareReplacementNotification) ->
        SetupHardwareCardModel(
          setupType = HardwareSetupType.Replace,
          onReplaceDevice = {
            props.onReplaceDevice()
          }
        )
      props.deviceInfo == null -> SetupHardwareCardModel(
        setupType = HardwareSetupType.PairNew,
        onReplaceDevice = {
          props.onReplaceDevice()
        }
      )
      else -> null
    }
  }
}
