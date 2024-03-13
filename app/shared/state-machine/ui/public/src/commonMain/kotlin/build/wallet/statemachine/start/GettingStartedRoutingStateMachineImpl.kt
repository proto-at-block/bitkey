package build.wallet.statemachine.start

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.isFullAccount
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.keybox.AccountData.StartIntent.BeTrustedContact
import build.wallet.statemachine.data.keybox.AccountData.StartIntent.RestoreBitkey
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.start.GettingStartedRoutingStateMachineImpl.State.BackupLoaded
import build.wallet.statemachine.start.GettingStartedRoutingStateMachineImpl.State.LoadingCloudBackup
import build.wallet.statemachine.start.GettingStartedRoutingStateMachineImpl.State.SignInFailure

class GettingStartedRoutingStateMachineImpl(
  private val accessCloudBackupUiStateMachine: AccessCloudBackupUiStateMachine,
) : GettingStartedRoutingStateMachine {
  @Composable
  override fun model(props: GettingStartedRoutingProps): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingCloudBackup) }

    val showErrorOnBackupMissing: Boolean =
      remember(props) {
        when (props.startIntent) {
          RestoreBitkey -> true
          BeTrustedContact -> false
        }
      }

    return when (val current = state) {
      is LoadingCloudBackup ->
        accessCloudBackupUiStateMachine.model(
          AccessCloudBackupUiProps(
            eakAssociation = props.eakAssociation,
            forceSignOutFromCloud = true,
            showErrorOnBackupMissing = showErrorOnBackupMissing,
            onExit = props.onExit,
            onBackupFound = { state = BackupLoaded(it) },
            onCannotAccessCloudBackup = { account ->
              state =
                if (account == null) {
                  SignInFailure
                } else {
                  BackupLoaded(null)
                }
            },
            onImportEmergencyAccessKit = { props.onImportEmergencyAccessKit() }
          )
        )
      is SignInFailure ->
        ErrorFormBodyModel(
          title = "You're not signed in",
          subline = "Sign in to your cloud account to use backups for your Bitkey account",
          primaryButton =
            ButtonDataModel(
              text = "Sign In",
              onClick = { state = LoadingCloudBackup }
            ),
          eventTrackerScreenId = null
        ).asRootScreen()
      is BackupLoaded -> {
        routeWithLoadedBackup(props, current.backup)

        LoadingBodyModel(
          id = null
        ).asRootScreen()
      }
    }
  }

  /**
   * Direct to the appropriate state machine based on the starting intent
   * and the current cloud account backup.
   */
  @Composable
  private fun routeWithLoadedBackup(
    props: GettingStartedRoutingProps,
    backup: CloudBackup?,
  ) {
    when (props.startIntent) {
      RestoreBitkey -> {
        when {
          backup == null -> props.onStartLostAppRecovery()
          backup.isFullAccount() -> props.onStartCloudRecovery(backup)
          else -> props.onStartLiteAccountRecovery(backup)
        }
      }
      BeTrustedContact -> {
        when {
          backup == null -> props.onStartLiteAccountCreation()
          backup.isFullAccount() -> props.onStartCloudRecovery(backup)
          else -> props.onStartLiteAccountRecovery(backup)
        }
      }
    }
  }

  private sealed interface State {
    data object LoadingCloudBackup : State

    data object SignInFailure : State

    data class BackupLoaded(val backup: CloudBackup?) : State
  }
}
