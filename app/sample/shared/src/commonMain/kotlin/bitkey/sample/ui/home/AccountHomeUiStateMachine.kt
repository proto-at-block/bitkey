package bitkey.sample.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.ui.settings.SettingsUiProps
import bitkey.sample.ui.settings.SettingsUiStateMachine
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

data class AccountHomeUiProps(
  val account: Account,
  val onExit: () -> Unit,
  val onDeleteAccount: () -> Unit,
)

interface AccountHomeUiStateMachine : StateMachine<AccountHomeUiProps, ScreenModel>

class AccountHomeUiStateMachineImpl(
  private val settingsUiStateMachine: SettingsUiStateMachine,
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
        settingsUiStateMachine.model(
          props = SettingsUiProps(
            account = props.account,
            onBack = {
              state = State.ViewingAccountHomeState
            },
            onDeleteAccount = props.onDeleteAccount
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
