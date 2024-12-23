package build.wallet.statemachine.data.recovery.losthardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps

@BitkeyInject(AppScope::class)
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
        // WHen completing recovery we are rotating auth keys which causes `props.account` to be
        // updated. We need to lock in the old auth key, so we are using `remember` here.
        // TODO(W-10369): refactor this DSM to Services pattern and remove this hack.
        val oldAppGlobalAuthKey = remember { props.account.keybox.activeAppKeyBundle.authKey }
        val recoveryInProgressData =
          recoveryInProgressDataStateMachine.model(
            props =
              RecoveryInProgressProps(
                fullAccountConfig = props.account.keybox.config,
                recovery = hardwareRecovery,
                oldAppGlobalAuthKey = oldAppGlobalAuthKey,
                onRetryCloudRecovery = null // Cloud Backup Recovery is not available for Lost Hardware.
              )
          )
        LostHardwareRecoveryInProgressData(recoveryInProgressData)
      }
    }
  }
}
