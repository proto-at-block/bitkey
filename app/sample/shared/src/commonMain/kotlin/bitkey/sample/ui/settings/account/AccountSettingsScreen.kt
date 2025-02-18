package bitkey.sample.ui.settings.account

import androidx.compose.runtime.*
import bitkey.sample.functional.Account
import bitkey.sample.functional.AccountRepository
import bitkey.sample.ui.error.ErrorScreen
import bitkey.sample.ui.settings.SettingsScreen
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.statemachine.core.ScreenModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

data class AccountSettingsScreen(
  val account: Account,
  val onAccountDeleted: () -> Unit,
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
                origin = screen
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
            onAccountDeleted = screen.onAccountDeleted
          )
        )
      }
    ).asRootScreen()
  }
}
