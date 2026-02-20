package build.wallet.statemachine.data.recovery.losthardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryDataStateMachineImpl(
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
) : LostHardwareRecoveryDataStateMachine {
  @Composable
  override fun model(props: LostHardwareRecoveryDataProps): LostHardwareRecoveryData {
    val hardwareRecovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    return when (val stillRecovering = hardwareRecovery) {
      is Recovery.StillRecovering -> {
        require(stillRecovering.factorToRecover == PhysicalFactor.Hardware)

        // Get the old app global auth key from the persisted recovery state if available,
        // otherwise fall back to the current keybox's auth key.
        // After auth key rotation, the keybox's auth key will be the NEW key, but the
        // recovery state will have the original key persisted.
        val oldAppGlobalAuthKey = stillRecovering.originalAppGlobalAuthKey
          ?: props.account.keybox.activeAppKeyBundle.authKey

        val recoveryInProgressData =
          recoveryInProgressDataStateMachine.model(
            props =
              RecoveryInProgressProps(
                recovery = stillRecovering,
                oldAppGlobalAuthKey = oldAppGlobalAuthKey
              )
          )
        LostHardwareRecoveryInProgressData(recoveryInProgressData)
      }
      else -> LostHardwareRecoveryData.LostHardwareRecoveryNotStarted
    }
  }
}
