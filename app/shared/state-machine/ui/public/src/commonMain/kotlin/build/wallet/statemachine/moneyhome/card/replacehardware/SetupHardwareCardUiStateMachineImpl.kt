package build.wallet.statemachine.moneyhome.card.replacehardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.fwup.FirmwareDataService
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.PostSocialRecoveryTaskState.*
import build.wallet.statemachine.moneyhome.card.CardModel

class SetupHardwareCardUiStateMachineImpl(
  val postSocRecTaskRepository: PostSocRecTaskRepository,
  private val firmwareDataService: FirmwareDataService,
) : SetupHardwareCardUiStateMachine {
  @Composable
  override fun model(props: SetupHardwareCardUiProps): CardModel? {
    val recoveryIncomplete = postSocRecTaskRepository.taskState.collectAsState(None)
    val firmwareData = remember {
      firmwareDataService.firmwareData()
    }.collectAsState().value

    return when {
      recoveryIncomplete.value in setOf(
        HardwareReplacementScreens,
        HardwareReplacementNotification
      ) ->
        SetupHardwareCardModel(
          setupType = HardwareSetupType.Replace,
          onReplaceDevice = {
            props.onReplaceDevice()
          }
        )
      firmwareData.firmwareDeviceInfo == null -> SetupHardwareCardModel(
        setupType = HardwareSetupType.PairNew,
        onReplaceDevice = {
          props.onReplaceDevice()
        }
      )
      else -> null
    }
  }
}
