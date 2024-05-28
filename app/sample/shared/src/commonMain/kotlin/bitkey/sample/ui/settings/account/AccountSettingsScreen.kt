package bitkey.sample.ui.settings.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.sample.functional.Account
import bitkey.sample.functional.AccountRepository
import bitkey.sample.ui.error.ErrorScreen
import bitkey.sample.ui.settings.SettingsScreen
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.framework.Navigator
import build.wallet.ui.framework.Screen
import build.wallet.ui.framework.ScreenPresenter
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

data class AccountSettingsScreen(
  val account: Account,
  val onAccountDeleted: () -> Unit,
  val onExit: () -> Unit,
) : Screen

class AccountSettingsScreenPresenter(
  private val accountRepository: AccountRepository,
) : ScreenPresenter<AccountSettingsScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: AccountSettingsScreen,
  ): ScreenModel {
    var deletingAccount by remember { mutableStateOf(false) }

    if (deletingAccount) {
      LaunchedEffect("delete-account") {
        accountRepository.removeActiveAccount()
          .onSuccess {
            screen.onAccountDeleted()
          }
          .onFailure {
            navigator.goTo(
              ErrorScreen(
                message = "Error deleting account: ${it.message}",
                exitScreen = screen
              )
            )
          }
        deletingAccount = false
      }
    }

    return AccountSettingsBodyModel(
      deletingAccount = deletingAccount,
      onDeleteAccountClick = {
        deletingAccount = true
      },
      onBack = {
        navigator.goTo(
          SettingsScreen(
            account = screen.account,
            onAccountDeleted = screen.onAccountDeleted,
            onExit = screen.onExit
          )
        )
      }
    ).asRootScreen()
  }
}
