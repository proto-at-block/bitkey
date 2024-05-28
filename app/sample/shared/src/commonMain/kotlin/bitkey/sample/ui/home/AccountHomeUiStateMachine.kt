package bitkey.sample.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.ui.settings.SettingsScreen
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.framework.NavigatorPresenter

data class AccountHomeUiProps(
  val account: Account,
  val onExit: () -> Unit,
  val onAccountDeleted: () -> Unit,
)

interface AccountHomeUiStateMachine : StateMachine<AccountHomeUiProps, ScreenModel>

class AccountHomeUiStateMachineImpl(
  private val navigatorPresenter: NavigatorPresenter,
) : AccountHomeUiStateMachine {
  @Composable
  override fun model(props: AccountHomeUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingAccountHomeState) }

    return when (state) {
      is State.ViewingAccountHomeState -> {
        AccountHomeBodyModel(
          accountName = props.account.name,
          accountId = props.account.id,
          onBack = props.onExit,
          onSettingsClick = {
            state = State.ViewingSettingsState
          }
        ).asRootScreen()
      }

      is State.ViewingSettingsState -> {
        navigatorPresenter.model(
          SettingsScreen(
            account = props.account,
            onExit = {
              state = State.ViewingAccountHomeState
            },
            onAccountDeleted = props.onAccountDeleted
          )
        )
      }
    }
  }

  private sealed interface State {
    data object ViewingAccountHomeState : State

    data object ViewingSettingsState : State
  }
}
