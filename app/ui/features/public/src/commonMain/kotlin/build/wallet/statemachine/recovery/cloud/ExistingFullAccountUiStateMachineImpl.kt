package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.*
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import com.github.michaelbull.result.get

@BitkeyInject(ActivityScope::class)
class ExistingFullAccountUiStateMachineImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
) : ExistingFullAccountUiStateMachine {
  @Composable
  override fun model(props: ExistingFullAccountUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ChoosingRestoreOrNew) }

    return when (val currentState = state) {
      State.ChoosingRestoreOrNew -> {
        ExistingFullAccountFoundBodyModel(
          devicePlatform = props.devicePlatform,
          onBack = props.onBack,
          onRestore = props.onRestore,
          onDeleteBackupAndCreateNew = {
            state = State.WarningAboutDeletingBackup
          }
        ).asRootScreen()
      }

      State.WarningAboutDeletingBackup -> {
        WarningAboutDeletingBackupBodyModel(
          onBack = { state = State.ChoosingRestoreOrNew },
          onContinue = {
            state = State.ConfirmingDeleteBackup(
              firstOptionEnabled = false,
              secondOptionEnabled = false
            )
          }
        ).asRootScreen()
      }
      is State.ConfirmingDeleteBackup -> ConfirmingDeleteBackupBodyModel(
        firstOptionIsConfirmed = currentState.firstOptionEnabled,
        secondOptionIsConfirmed = currentState.secondOptionEnabled,
        onBack = { state = State.WarningAboutDeletingBackup },
        onClickFirstOption = {
          state = currentState.copy(firstOptionEnabled = currentState.firstOptionEnabled.not())
        },
        onClickSecondOption = {
          state = currentState.copy(secondOptionEnabled = currentState.secondOptionEnabled.not())
        },
        onConfirmDelete = {
          state = State.ArchivingBackup
        }
      ).asRootScreen()

      State.ArchivingBackup -> {
        LaunchedEffect("archiving-backup") {
          val cloudStoreAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
            .get()

          if (cloudStoreAccount == null) {
            state = State.ChoosingRestoreOrNew
            return@LaunchedEffect
          }

          cloudBackupRepository.archiveBackup(
            cloudStoreAccount = cloudStoreAccount,
            backup = props.cloudBackup
          )

          cloudBackupRepository.clear(
            cloudStoreAccount,
            clearRemoteOnly = false
          )

          props.onBackupArchive()
        }

        LoadingBodyModel(
          message = "Archiving backup...",
          primaryButton = null,
          id = null
        ).asRootScreen()
      }
    }
  }
}

private sealed interface State {
  data object ChoosingRestoreOrNew : State

  data object WarningAboutDeletingBackup : State

  data class ConfirmingDeleteBackup(val firstOptionEnabled: Boolean, val secondOptionEnabled: Boolean) : State

  data object ArchivingBackup : State
}
