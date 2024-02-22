package build.wallet.statemachine.data.recovery.losthardware

import androidx.compose.runtime.Composable
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps

class LostHardwareRecoveryDataStateMachineImpl(
  private val initiatingLostHardwareRecoveryDataStateMachine:
    InitiatingLostHardwareRecoveryDataStateMachine,
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
) : LostHardwareRecoveryDataStateMachine {
  @Composable
  override fun model(props: LostHardwareRecoveryProps): LostHardwareRecoveryData {
    return when (val hardwareRecovery = props.hardwareRecovery) {
      null ->
        initiatingLostHardwareRecoveryDataStateMachine.model(
          props = InitiatingLostHardwareRecoveryProps(props.account)
        )

      else -> {
        val recoveryInProgressData =
          recoveryInProgressDataStateMachine.model(
            props =
              RecoveryInProgressProps(
                keyboxConfig = props.account.keybox.config,
                recovery = hardwareRecovery,
                onRetryCloudRecovery = null // Cloud Backup Recovery is not available for Lost Hardware.
              )
          )
        LostHardwareRecoveryInProgressData(recoveryInProgressData)
      }
    }
  }
}
