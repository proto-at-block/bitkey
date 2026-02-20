package build.wallet.statemachine.data.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryProps

@BitkeyInject(AppScope::class)
class LostAppRecoveryDataStateMachineImpl(
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
  private val initiatingLostAppRecoveryDataStateMachine: InitiatingLostAppRecoveryDataStateMachine,
) : LostAppRecoveryDataStateMachine {
  @Composable
  override fun model(props: LostAppRecoveryProps): LostAppRecoveryData {
    return when (val activeRecovery = props.activeRecovery) {
      null -> initiatingLostAppRecoveryDataStateMachine.model(
        InitiatingLostAppRecoveryProps(
          cloudBackups = props.cloudBackups,
          onRollback = props.onRollback,
          goToLiteAccountCreation = props.goToLiteAccountCreation
        )
      )

      else -> LostAppRecoveryInProgressData(
        recoveryInProgressData = recoveryInProgressDataStateMachine.model(
          RecoveryInProgressProps(
            recovery = activeRecovery,
            oldAppGlobalAuthKey = null
          )
        )
      )
    }
  }
}
