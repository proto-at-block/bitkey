package bitkey.ui.screens.onboarding

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.ScreenModel

/**
 * Initial onboarding screen shown to the customer after app loading
 * splash screen, if they don't have active account or recovery.
 */
data object WelcomeScreen : Screen

@BitkeyInject(ActivityScope::class)
class WelcomeScreenPresenter(
  private val appVariant: AppVariant,
) : ScreenPresenter<WelcomeScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: WelcomeScreen,
  ): ScreenModel {
    return ChooseAccountAccessModel(
      onLogoClick = {
        // Only enable the debug menu in non-customer builds
        when (appVariant) {
          Customer -> navigator.goTo(DemoModeDisabledScreen)
          Team, Development, Alpha -> {
            // TODO: show debug menu
          }
          else -> Unit
        }
      },
      onSetUpNewWalletClick = {
        // TODO: implement
      },
      onMoreOptionsClick = {
        navigator.goTo(AccountAccessOptionsScreen)
      }
    ).asRootFullScreen()
  }
}
