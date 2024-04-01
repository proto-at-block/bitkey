package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.FAILURE_RESTORE_FROM_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.LiteAccountCloudBackupRestorer
import build.wallet.logging.logFailure
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiState.Failure
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiState.Restoring
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/** Restore a lite account via cloud backup */
class LiteAccountCloudBackupRestorationUiStateMachineImpl(
  private val liteAccountCloudBackupRestorer: LiteAccountCloudBackupRestorer,
) : LiteAccountCloudBackupRestorationUiStateMachine {
  @Composable
  override fun model(props: LiteAccountCloudBackupRestorationUiProps): ScreenModel {
    require(props.cloudBackup is CloudBackupV2) {
      "Only CloudBackupV2 is supported for lite account restoration"
    }

    var uiState: LiteAccountCloudBackupRestorationUiState by remember {
      mutableStateOf(Restoring)
    }

    return when (val state = uiState) {
      Restoring -> {
        LaunchedEffect("recovering-via-lite-account-cloud-backup") {
          liteAccountCloudBackupRestorer
            .restoreFromBackup(liteAccountCloudBackup = props.cloudBackup)
            .logFailure { "Failed to recover lite account via cloud backup" }
            .onFailure {
              uiState = Failure(cause = it)
            }
            .onSuccess(props.onLiteAccountRestored)
        }
        LoadingBodyModel(
          message = null,
          onBack = null,
          id = LOADING_RESTORING_FROM_CLOUD_BACKUP
        ).asRootScreen()
      }

      is Failure -> {
        ErrorFormBodyModel(
          title = "We were unable to restore your wallet from a backup",
          primaryButton = ButtonDataModel(text = "Back", onClick = props.onExit),
          eventTrackerScreenId = FAILURE_RESTORE_FROM_CLOUD_BACKUP,
          errorData = ErrorData(
            segment = RecoverySegment.CloudBackup.LiteAccount.Restoration,
            cause = state.cause,
            actionDescription = "Restoring lite account from cloud backup"
          )
        ).asRootScreen()
      }
    }
  }
}

private sealed interface LiteAccountCloudBackupRestorationUiState {
  /** Restoring lite account */
  data object Restoring : LiteAccountCloudBackupRestorationUiState

  /** Failed to restore lite account */
  data class Failure(val cause: Error) : LiteAccountCloudBackupRestorationUiState
}
