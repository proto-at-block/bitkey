package bitkey.sample.ui.settings.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface AccountSettingsUiStateMachine : StateMachine<AccountSettingsUiProps, ScreenModel>

data class AccountSettingsUiProps(
  val account: Account,
  val onExit: () -> Unit,
  val onDeleteAccount: () -> Unit,
)

class AccountSettingsUiStateMachineImpl : AccountSettingsUiStateMachine {
  @Composable
  override fun model(props: AccountSettingsUiProps): ScreenModel {
    var deletingAccount by remember { mutableStateOf(false) }

    return AccountSettingsBodyModel(
      onBack = props.onExit,
      deletingAccount = deletingAccount,
      onDeleteAccountClick = {
        deletingAccount = true
        props.onDeleteAccount()
      }
    ).asModalScreen()
  }
}
