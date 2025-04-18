package bitkey.ui.screens.securityhub

import androidx.compose.runtime.*
import bitkey.securitycenter.SecurityAction
import bitkey.securitycenter.SecurityActionCategory
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionType.*
import bitkey.securitycenter.SecurityActionsService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import build.wallet.ui.model.status.StatusBannerModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class SecurityHubScreen(
  val homeStatusBannerModel: StatusBannerModel?,
  // TODO W-11082 Update tabs interface for supporting the new tab bar
  val tabs: List<HomeTab>,
) : Screen

@BitkeyInject(ActivityScope::class)
class SecurityHubPresenter(
  private val securityActionsService: SecurityActionsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
) : ScreenPresenter<SecurityHubScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: SecurityHubScreen,
  ): ScreenModel {
    var securityActions by remember { mutableStateOf(emptyList<SecurityAction>()) }
    var recoveryActions by remember { mutableStateOf(emptyList<SecurityAction>()) }
    val recommendations by remember {
      securityActionsService.getRecommendations()
    }.collectAsState(emptyList())

    var isRefreshing by remember { mutableStateOf(true) }

    if (isRefreshing) {
      LaunchedEffect("update-actions-and-recommendations") {
        withMinimumDelay(minimumLoadingDuration.value) {
          coroutineScope {
            launch {
              securityActions = securityActionsService.getActions(SecurityActionCategory.SECURITY)
            }
            launch {
              recoveryActions = securityActionsService.getActions(SecurityActionCategory.RECOVERY)
            }
          }
        }
        isRefreshing = false
      }
    }

    return SecurityHubBodyModel(
      isRefreshing = isRefreshing,
      onRefresh = { isRefreshing = true },
      recommendations = recommendations.toImmutableList(),
      tabs = screen.tabs,
      securityActions = securityActions,
      recoveryActions = recoveryActions,
      onRecommendationClick = {
        Router.route = Route.NavigationDeeplink(screen = it.navigationScreenId())
      },
      onSecurityActionClick = {
        Router.route = Route.NavigationDeeplink(screen = it.navigationScreenId())
      }
    ).asRootFullScreen(statusBannerModel = screen.homeStatusBannerModel)
  }
}

private fun SecurityAction.navigationScreenId(): NavigationScreenId =
  when (this.type()) {
    BIOMETRIC -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC
    CRITICAL_ALERTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
    EAK_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH
    FINGERPRINTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS
    INHERITANCE -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE
    MOBILE_KEY_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
    SOCIAL_RECOVERY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS
  }

fun SecurityActionRecommendation.navigationScreenId(): NavigationScreenId =
  when (this) {
    BACKUP_MOBILE_KEY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
    BACKUP_EAK -> NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH
    ADD_FINGERPRINTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS
    ADD_TRUSTED_CONTACTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS
    ENABLE_CRITICAL_ALERTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
    ADD_BENEFICIARY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE
    SETUP_BIOMETRICS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC
  }
