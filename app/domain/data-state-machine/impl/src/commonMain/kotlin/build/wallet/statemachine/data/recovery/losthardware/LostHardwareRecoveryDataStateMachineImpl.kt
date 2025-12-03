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

        // WHen completing recovery we are rotating auth keys which causes `props.account` to be
        // updated. We need to lock in the old auth key, so we are using `remember` here.
        // TODO(W-10369): refactor this DSM to Services pattern and remove this hack.
        val oldAppGlobalAuthKey = remember { props.account.keybox.activeAppKeyBundle.authKey }
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
