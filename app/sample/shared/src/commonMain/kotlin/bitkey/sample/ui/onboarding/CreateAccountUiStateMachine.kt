package bitkey.sample.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.functional.AccountRepository
import bitkey.sample.ui.model.LoadingBodyModel
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl.State.AccountCreatedState
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl.State.CreatingAccountState
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachineImpl.State.RequestingAccountNameState
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

data class CreateAccountUiProps(
  val onExit: () -> Unit,
  val onAccountCreated: (Account) -> Unit,
)

interface CreateAccountUiStateMachine : StateMachine<CreateAccountUiProps, ScreenModel>

class CreateAccountUiStateMachineImpl(
  private val createAccountRepository: AccountRepository,
) : CreateAccountUiStateMachine {
  @Composable
  override fun model(props: CreateAccountUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(RequestingAccountNameState)
    }

    return when (val currentState = state) {
      is RequestingAccountNameState ->
        RequestAccountNameBodyModel(
          onBack = props.onExit,
          onEnterAccountName = {
            state = CreatingAccountState(it)
          }
        ).asModalScreen()
      is CreatingAccountState -> {
        LaunchedEffect("create-account") {
          createAccountRepository
            .createAccount(name = currentState.accountName)
            .onSuccess(props.onAccountCreated)
            .onFailure {
              state = RequestingAccountNameState
            }
        }

        LoadingBodyModel(
          message = "Creating account...",
          onBack = {
            // noop
          }
        ).asModalScreen()
      }
      is AccountCreatedState -> {
        AccountCreatedBodyModel(
          accountName = currentState.account.name,
          onContinue = {
            props.onAccountCreated(currentState.account)
          }
        ).asModalScreen()
      }
    }
  }

  private sealed interface State {
    data object RequestingAccountNameState : State

    data class CreatingAccountState(val accountName: String) : State

    data class AccountCreatedState(
      val account: Account,
    ) : State
  }
}
