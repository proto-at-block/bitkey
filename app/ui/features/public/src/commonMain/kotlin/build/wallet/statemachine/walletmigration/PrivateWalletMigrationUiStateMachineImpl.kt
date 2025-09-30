package build.wallet.statemachine.walletmigration

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel

@BitkeyInject(ActivityScope::class)
class PrivateWalletMigrationUiStateMachineImpl : PrivateWalletMigrationUiStateMachine {
  @Composable
  override fun model(props: PrivateWalletMigrationUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf<PrivateWalletMigrationUiState>(PrivateWalletMigrationUiState.Introduction)
    }

    return when (uiState) {
      is PrivateWalletMigrationUiState.Introduction -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = props.onExit,
            onContinue = {
              uiState = PrivateWalletMigrationUiState.Success
            },
            onLearnMore = {
              // TODO: Implement learn more navigation when ready
            }
          )
        )
      }

      is PrivateWalletMigrationUiState.CreatingKeyset -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Creating your private wallet...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.PreparingSweep -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Preparing to move your funds...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.BroadcastingTransaction -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Broadcasting transaction...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.WaitingForConfirmations -> {
        val waitingState = uiState as PrivateWalletMigrationUiState.WaitingForConfirmations
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Waiting for confirmations... (${waitingState.confirmations}/${waitingState.requiredConfirmations})",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.Finalizing -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Finalizing your private wallet...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.Success -> {
        ScreenModel(
          body = PrivateWalletMigrationCompleteBodyModel(
            onBack = props.onExit,
            onComplete = props.onExit
          )
        )
      }

      is PrivateWalletMigrationUiState.Error -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Migration Error",
            subline = "There was an error migrating your wallet. Please try again.",
            primaryButton = ButtonDataModel(
              text = "Retry",
              onClick = {
                uiState = PrivateWalletMigrationUiState.Introduction
              }
            ),
            secondaryButton = ButtonDataModel(
              text = "Cancel",
              onClick = props.onExit
            ),
            eventTrackerScreenId = null
          )
        )
      }
    }
  }
}

private sealed interface PrivateWalletMigrationUiState {
  /**
   * Initial introduction screen explaining the migration.
   */
  data object Introduction : PrivateWalletMigrationUiState

  /**
   * Creating new private keyset.
   */
  data object CreatingKeyset : PrivateWalletMigrationUiState

  /**
   * Preparing sweep transaction.
   */
  data object PreparingSweep : PrivateWalletMigrationUiState

  /**
   * Broadcasting sweep transaction.
   */
  data class BroadcastingTransaction(val txid: String) : PrivateWalletMigrationUiState

  /**
   * Waiting for transaction confirmations.
   */
  data class WaitingForConfirmations(
    val txid: String,
    val confirmations: Int,
    val requiredConfirmations: Int,
  ) : PrivateWalletMigrationUiState

  /**
   * Finalizing migration.
   */
  data object Finalizing : PrivateWalletMigrationUiState

  /**
   * Migration completed successfully.
   */
  data object Success : PrivateWalletMigrationUiState

  /**
   * Migration failed with error.
   */
  data object Error : PrivateWalletMigrationUiState
}
