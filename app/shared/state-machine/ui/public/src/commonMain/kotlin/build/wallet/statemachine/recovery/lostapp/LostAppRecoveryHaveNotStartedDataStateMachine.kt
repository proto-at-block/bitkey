package build.wallet.statemachine.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.cloud.RecoveringKeyboxFromCloudBackupData.AccessingCloudBackupData
import build.wallet.statemachine.data.recovery.lostapp.cloud.RecoveringKeyboxFromCloudBackupData.RecoveringFromCloudBackupData
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachine

/**
 * A UI state machine when the customer has engaged with the Lost App recovery process but has not yet
 * officially started a recovery on the server.
 */
interface LostAppRecoveryHaveNotStartedUiStateMachine : StateMachine<LostAppRecoveryHaveNotStartedUiProps, ScreenModel>

data class LostAppRecoveryHaveNotStartedUiProps(
  val notUndergoingRecoveryData: LostAppRecoveryData.LostAppRecoveryHaveNotStartedData,
  val keyboxConfig: KeyboxConfig,
  val eakAssociation: EmergencyAccessKitAssociation,
)

class LostAppRecoveryHaveNotStartedUiStateMachineImpl(
  private val initiatingLostAppRecoveryUiStateMachine: InitiatingLostAppRecoveryUiStateMachine,
  private val accessCloudBackupUiStateMachine: AccessCloudBackupUiStateMachine,
  private val fullAccountCloudBackupRestorationUiStateMachine:
    FullAccountCloudBackupRestorationUiStateMachine,
) : LostAppRecoveryHaveNotStartedUiStateMachine {
  @Composable
  override fun model(props: LostAppRecoveryHaveNotStartedUiProps): ScreenModel {
    return when (props.notUndergoingRecoveryData) {
      is AttemptingCloudRecoveryLostAppRecoveryDataData -> {
        when (val recoveringFromCloudBackupData = props.notUndergoingRecoveryData.data) {
          is AccessingCloudBackupData ->
            accessCloudBackupUiStateMachine.model(
              props =
                AccessCloudBackupUiProps(
                  eakAssociation = props.eakAssociation,
                  forceSignOutFromCloud = false,
                  onBackupFound = { backup ->
                    recoveringFromCloudBackupData.onCloudBackupFound(backup)
                  },
                  onCannotAccessCloudBackup = {
                    recoveringFromCloudBackupData.onCloudBackupNotAvailable()
                  },
                  onImportEmergencyAccessKit = {
                    recoveringFromCloudBackupData.onImportEmergencyAccessKit()
                  },
                  onExit = recoveringFromCloudBackupData.rollback
                )
            )

          is RecoveringFromCloudBackupData -> {
            fullAccountCloudBackupRestorationUiStateMachine.model(
              props =
                FullAccountCloudBackupRestorationUiProps(
                  keyboxConfig = props.keyboxConfig,
                  backup = recoveringFromCloudBackupData.cloudBackup,
                  onExit = recoveringFromCloudBackupData.rollback
                )
            )
          }
        }
      }

      is InitiatingLostAppRecoveryData ->
        initiatingLostAppRecoveryUiStateMachine.model(
          InitiatingLostAppRecoveryUiProps(
            keyboxConfig = props.keyboxConfig,
            initiatingLostAppRecoveryData = props.notUndergoingRecoveryData
          )
        )
    }
  }
}
