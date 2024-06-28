package build.wallet.statemachine.account.create

import androidx.compose.runtime.*
import build.wallet.onboarding.CreateSoftwareWalletWorkflow
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.statemachine.core.ScreenModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class CreateSoftwareWalletUiStateMachineImpl(
  private val createSoftwareWalletWorkflow: CreateSoftwareWalletWorkflow,
) : CreateSoftwareWalletUiStateMachine {
  @Composable
  override fun model(props: CreateSoftwareWalletProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.CreatingAccount) }

    return when (uiState) {
      is State.CreatingAccount -> {
        LaunchedEffect("create-account") {
          createSoftwareWalletWorkflow
            .createAccount()
            .onSuccess {
              uiState = State.AccountCreated
            }
            .onFailure {
              props.onExit()
            }
        }
        LoadingBodyModel(
          id = null,
          onBack = null,
          message = "Creating Software Wallet..."
        ).asRootScreen()
      }

      is State.AccountCreated -> {
        LoadingSuccessBodyModel(
          message = "Software Wallet Created",
          state = Success,
          id = null
        ).asRootScreen()
      }
    }
  }

  private sealed interface State {
    data object CreatingAccount : State

    data object AccountCreated : State
  }
}