package bitkey.ui.screens.securityhub.education

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import bitkey.securitycenter.SecurityAction
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.securityhub.SecurityHubScreen
import bitkey.ui.screens.securityhub.SecurityHubUiState
import bitkey.ui.screens.securityhub.navigateToScreen
import bitkey.ui.screens.securityhub.navigationScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.ScreenModel

/**
 * Screen for displaying educational content related to security actions in the Security Hub.
 *
 * @property action The security action that triggered this screen.
 * @property originScreen The screen from which the user navigated to this screen.
 * @property firmwareData The firmware update state data.
 * @property onStateChange Callback function to communicate state changes back to the SecurityHub presenter.
 */
sealed class SecurityHubEducationScreen(
  open val originScreen: SecurityHubScreen,
  open val firmwareData: FirmwareData.FirmwareUpdateState,
  open val onStateChange: ((SecurityHubUiState) -> Unit)? = null,
) : Screen {
  data class ActionEducation(
    val action: SecurityAction,
    override val originScreen: SecurityHubScreen,
    override val firmwareData: FirmwareData.FirmwareUpdateState,
    override val onStateChange: ((SecurityHubUiState) -> Unit)? = null,
  ) : SecurityHubEducationScreen(originScreen, firmwareData, onStateChange)

  data class RecommendationEducation(
    val recommendation: SecurityActionRecommendation,
    override val originScreen: SecurityHubScreen,
    override val firmwareData: FirmwareData.FirmwareUpdateState,
    override val onStateChange: ((SecurityHubUiState) -> Unit)? = null,
  ) : SecurityHubEducationScreen(originScreen, firmwareData, onStateChange)
}

@BitkeyInject(ActivityScope::class)
class SecurityHubEducationScreenPresenter(
  val fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
) : ScreenPresenter<SecurityHubEducationScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: SecurityHubEducationScreen,
  ): ScreenModel {
    val navigationId = when (screen) {
      is SecurityHubEducationScreen.ActionEducation -> screen.action.navigationScreenId()
      is SecurityHubEducationScreen.RecommendationEducation -> screen.recommendation.navigationScreenId()
    }

    val isFingerprintResetEnabled by fingerprintResetFeatureFlag.flagValue().collectAsState()

    return SecurityHubEducationBodyModel(
      actionType = when (screen) {
        is SecurityHubEducationScreen.ActionEducation -> screen.action.type()
        is SecurityHubEducationScreen.RecommendationEducation -> screen.recommendation.actionType
      },
      onBack = {
        navigator.goTo(
          SecurityHubScreen(
            account = screen.originScreen.account,
            hardwareRecoveryData = screen.originScreen.hardwareRecoveryData
          )
        )
      },
      onContinue = {
        navigator.navigateToScreen(
          id = navigationId,
          originScreen = screen.originScreen,
          firmwareUpdateData = screen.firmwareData,
          isFingerprintResetEnabled = isFingerprintResetEnabled.value,
          onCannotUnlockFingerprints = {
            navigator.goTo(
              SecurityHubScreen(
                account = screen.originScreen.account,
                hardwareRecoveryData = screen.originScreen.hardwareRecoveryData,
                initialState = SecurityHubUiState.FingerprintResetState
              )
            )
          }
        )
      }
    ).asModalScreen()
  }
}
