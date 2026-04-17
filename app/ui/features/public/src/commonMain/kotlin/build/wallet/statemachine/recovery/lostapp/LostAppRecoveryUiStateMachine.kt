package build.wallet.statemachine.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.cloud.backup.CloudBackup
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachine

/**
 * The top level recovery UI state machine within the onboarding flow. Houses both the cloud
 * recovery and lost app recovery experiences.
 */
interface LostAppRecoveryUiStateMachine : StateMachine<LostAppRecoveryUiProps, ScreenModel>

/**
 * @property cloudBackups List of cloud backups to try during recovery. The restoration flow will
 * attempt to decrypt each backup with the hardware key until one succeeds.
 * @property activeRecovery Existing recovery in progress, if any.
 * @property onRollback Called when recovery is rolled back.
 * @property goToLiteAccountCreation Called to navigate to lite account creation.
 */
data class LostAppRecoveryUiProps(
  val cloudBackups: List<CloudBackup>,
  val activeRecovery: StillRecovering?,
  val onRollback: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)

@BitkeyInject(ActivityScope::class)
class LostAppRecoveryUiStateMachineImpl(
  private val recoveryInProgressDataStateMachine: RecoveryInProgressDataStateMachine,
  private val initiatingLostAppRecoveryUiStateMachine: InitiatingLostAppRecoveryUiStateMachine,
  private val recoveryInProgressUiStateMachine: RecoveryInProgressUiStateMachine,
) : LostAppRecoveryUiStateMachine {
  @Composable
  override fun model(props: LostAppRecoveryUiProps): ScreenModel {
    return when (val activeRecovery = props.activeRecovery) {
      null ->
        initiatingLostAppRecoveryUiStateMachine.model(
          InitiatingLostAppRecoveryUiProps(
            cloudBackups = props.cloudBackups,
            onRollback = props.onRollback,
            goToLiteAccountCreation = props.goToLiteAccountCreation
          )
        )

      else -> {
        val recoveryInProgressData = recoveryInProgressDataStateMachine.model(
          RecoveryInProgressProps(
            recovery = activeRecovery,
            oldAppGlobalAuthKey = null
          )
        )
        recoveryInProgressUiStateMachine.model(
          RecoveryInProgressUiProps(
            presentationStyle = Root,
            recoveryInProgressData = recoveryInProgressData
          )
        )
      }
    }
  }
}
