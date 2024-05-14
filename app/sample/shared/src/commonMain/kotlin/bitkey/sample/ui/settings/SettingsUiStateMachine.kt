package bitkey.sample.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.ui.settings.SettingsListBodyModel.SettingsRowModel
import bitkey.sample.ui.settings.SettingsUiStateMachineImpl.State.ViewingAllSettingsState
import bitkey.sample.ui.settings.account.AccountSettingsUiStateMachine
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface SettingsUiStateMachine : StateMachine<SettingsUiProps, ScreenModel>

data class SettingsUiProps(
  val account: Account,
  val onBack: () -> Unit,
  val onDeleteAccount: () -> Unit,
)

class SettingsUiStateMachineImpl(
  private val accountSettingsUiStateMachine: AccountSettingsUiStateMachine,
) : SettingsUiStateMachine {
  @Composable
  override fun model(props: SettingsUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ViewingAllSettingsState) }

    return when (state) {
      is ViewingAllSettingsState -> {
        SettingsListBodyModel(
          onBack = props.onBack,
          rows = immutableListOf(
            SettingsRowModel(
              title = "Account",
              onClick = {
                state = State.ViewingAccountSettingsState
              }
            )
          )
        ).asRootScreen()
      }

      is State.ViewingAccountSettingsState -> {
        accountSettingsUiStateMachine.model(
          props = bitkey.sample.ui.settings.account.AccountSettingsUiProps(
            account = props.account,
            onExit = {
              state = ViewingAllSettingsState
            },
            onDeleteAccount = props.onDeleteAccount
          )
        )
      }
    }
  }

  private sealed interface State {
    data object ViewingAllSettingsState : State

    data object ViewingAccountSettingsState : State
  }
}
