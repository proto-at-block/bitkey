package bitkey.sample.ui.settings

import androidx.compose.runtime.Composable
import bitkey.sample.functional.Account
import bitkey.sample.ui.settings.SettingsListBodyModel.SettingsRowModel
import bitkey.sample.ui.settings.account.AccountSettingsScreen
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.SimpleScreen
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.ScreenModel

data class SettingsScreen(
  val account: Account,
  val onAccountDeleted: () -> Unit,
) : SimpleScreen {
  @Composable
  override fun model(navigator: Navigator): ScreenModel {
    return SettingsListBodyModel(
      rows = immutableListOf(
        SettingsRowModel(
          title = "Account",
          onClick = {
            navigator.goTo(
              AccountSettingsScreen(
                account = account,
                onAccountDeleted = onAccountDeleted
              )
            )
          }
        )
      ),
      onBack = navigator::exit
    ).asRootScreen()
  }
}
