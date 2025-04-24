package bitkey.ui.screens.onboarding

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.core.ScreenModel

/**
 * Options for accessing an existing account (recovery), or becoming
 * a trusted contact.
 */
data object AccountAccessOptionsScreen : Screen

@BitkeyInject(ActivityScope::class)
class AccountAccessOptionsScreenPresenter(
  private val inheritanceFeatureFlag: InheritanceFeatureFlag,
) : ScreenPresenter<AccountAccessOptionsScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: AccountAccessOptionsScreen,
  ): ScreenModel {
    return AccountAccessMoreOptionsFormBodyModel(
      onBack = {
        navigator.goTo(WelcomeScreen)
      },
      onRestoreYourWalletClick = {
        // TODO: implement
      },
      onBeTrustedContactClick = {
        // TODO: implement
      },
      onResetExistingDevice = {
        // TODO: implement
      },
      isInheritanceEnabled = inheritanceFeatureFlag.isEnabled()
    ).asRootFullScreen()
  }
}
