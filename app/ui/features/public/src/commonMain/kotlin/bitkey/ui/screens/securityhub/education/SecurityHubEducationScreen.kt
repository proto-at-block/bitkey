package bitkey.ui.screens.securityhub.education

import androidx.compose.runtime.Composable
import bitkey.securitycenter.SecurityAction
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.securityhub.SecurityHubScreen
import bitkey.ui.screens.securityhub.navigateToScreen
import bitkey.ui.screens.securityhub.navigationScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.ScreenModel

/**
 * Screen for displaying educational content related to security actions in the Security Hub.
 *
 * @property action The security action that triggered this screen.
 * @property originScreen The screen from which the user navigated to this screen.
 * @property firmwareData The firmware update state data.
 */
sealed class SecurityHubEducationScreen(
  open val originScreen: SecurityHubScreen,
  open val firmwareData: FirmwareData.FirmwareUpdateState,
) : Screen {
  data class ActionEducation(
    val action: SecurityAction,
    override val originScreen: SecurityHubScreen,
    override val firmwareData: FirmwareData.FirmwareUpdateState,
  ) : SecurityHubEducationScreen(originScreen, firmwareData)

  data class RecommendationEducation(
    val recommendation: SecurityActionRecommendation,
    override val originScreen: SecurityHubScreen,
    override val firmwareData: FirmwareData.FirmwareUpdateState,
  ) : SecurityHubEducationScreen(originScreen, firmwareData)
}

@BitkeyInject(ActivityScope::class)
class SecurityHubEducationScreenPresenter : ScreenPresenter<SecurityHubEducationScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: SecurityHubEducationScreen,
  ): ScreenModel {
    val navigationId = when (screen) {
      is SecurityHubEducationScreen.ActionEducation -> screen.action.navigationScreenId()
      is SecurityHubEducationScreen.RecommendationEducation -> screen.recommendation.navigationScreenId()
    }

    return SecurityHubEducationBodyModel(
      actionType = when (screen) {
        is SecurityHubEducationScreen.ActionEducation -> screen.action.type()
        is SecurityHubEducationScreen.RecommendationEducation -> screen.recommendation.actionType
      },
      onBack = { navigator.goTo(screen.originScreen) },
      onContinue = {
        navigator.navigateToScreen(navigationId, screen.originScreen, screen.firmwareData)
      }
    ).asModalScreen()
  }
}
