package bitkey.sample.ui.home

import androidx.compose.runtime.*
import bitkey.sample.functional.Account
import bitkey.sample.ui.settings.SettingsScreen
import bitkey.ui.framework.NavigatorPresenter
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

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
            onAccountDeleted = props.onAccountDeleted
          ),
          onExit = {
            state = State.ViewingAccountHomeState
          }
        )
      }
    }
  }

  private sealed interface State {
    data object ViewingAccountHomeState : State

    data object ViewingSettingsState : State
  }
}
