package bitkey.sample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.functional.AccountRepository
import bitkey.sample.ui.SampleAppUiStateMachineImpl.State.CreatingAccountState
import bitkey.sample.ui.SampleAppUiStateMachineImpl.State.HasActiveAccountState
import bitkey.sample.ui.SampleAppUiStateMachineImpl.State.LoadingAppState
import bitkey.sample.ui.SampleAppUiStateMachineImpl.State.NoActiveAccountState
import bitkey.sample.ui.home.AccountHomeUiProps
import bitkey.sample.ui.home.AccountHomeUiStateMachine
import bitkey.sample.ui.model.LoadingBodyModel
import bitkey.sample.ui.onboarding.CreateAccountUiProps
import bitkey.sample.ui.onboarding.CreateAccountUiStateMachine
import bitkey.sample.ui.onboarding.WelcomeBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

interface SampleAppUiStateMachine : StateMachine<Unit, ScreenModel>

class SampleAppUiStateMachineImpl(
  private val accountRepository: AccountRepository,
  private val accountHomeUiStateMachine: AccountHomeUiStateMachine,
  private val createAccountUiStateMachine: CreateAccountUiStateMachine,
) : SampleAppUiStateMachine {
  @Composable
  override fun model(props: Unit): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingAppState) }

    return when (val currentState = state) {
      is LoadingAppState -> {
        LaunchedEffect("load-app-state") {
          accountRepository.activeAccount()
            .collect { account ->
              state = when {
                account != null -> HasActiveAccountState(account)
                else -> NoActiveAccountState
              }
            }
        }
        LoadingBodyModel(
          message = "Loading app...",
          onBack = {
            // todo: exit app
          }
        ).asRootScreen()
      }
      is CreatingAccountState -> {
        val scope = rememberCoroutineScope()
        createAccountUiStateMachine.model(
          props = CreateAccountUiProps(
            onExit = {
              state = NoActiveAccountState
            },
            onAccountCreated = { account ->
              scope.launch {
                accountRepository
                  .activateAccount(account)
                  .onSuccess {
                    state = HasActiveAccountState(account)
                  }
              }
            }
          )
        )
      }
      is NoActiveAccountState -> {
        WelcomeBodyModel(
          onBack = {
            // todo: exit app
          },
          onCreateAccount = {
            state = CreatingAccountState
          }
        ).asRootScreen()
      }
      is HasActiveAccountState -> {
        val scope = rememberCoroutineScope()
        accountHomeUiStateMachine.model(
          AccountHomeUiProps(
            account = currentState.activeAccount,
            onDeleteAccount = {
              scope.launch {
                accountRepository.removeActiveAccount()
                  .onSuccess {
                    state = NoActiveAccountState
                  }
                  .onFailure {
                    // todo: show error screen
                  }
              }
            },
            onExit = {
              // todo: exit app
            }
          )
        )
      }
    }
  }

  private sealed interface State {
    data object LoadingAppState : State

    data object NoActiveAccountState : State

    data class HasActiveAccountState(
      val activeAccount: Account,
    ) : State

    data object CreatingAccountState : State
  }
}
