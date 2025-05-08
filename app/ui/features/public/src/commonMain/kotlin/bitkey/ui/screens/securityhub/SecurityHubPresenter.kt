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
import bitkey.ui.screens.device.DeviceSettingsScreen
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreen
import bitkey.ui.screens.securityhub.education.SecurityHubEducationScreen
import bitkey.ui.screens.securityhub.education.SecurityHubEducationScreen.ActionEducation
import bitkey.ui.sheets.ViewInvitationSheet
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.compose.collections.buildImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.biometric.BiometricSettingScreen
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreen
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.notifications.Source
import build.wallet.statemachine.status.AppFunctionalityStatusScreen
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// TODO remove dependency on full account when children no longer need it
data class SecurityHubScreen(
  val account: FullAccount,
  val hardwareRecoveryData: LostHardwareRecoveryData,
) : Screen

@BitkeyInject(ActivityScope::class)
class SecurityHubPresenter(
  private val securityActionsService: SecurityActionsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val homeStatusBannerUiStateMachine: HomeStatusBannerUiStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val recoveryContactCardsUiStateMachine: RecoveryContactCardsUiStateMachine,
  private val hardwareRecoveryStatusCardUiStateMachine: HardwareRecoveryStatusCardUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
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

    val homeStatusBannerModel = homeStatusBannerUiStateMachine.model(
      props = HomeStatusBannerUiProps(
        onBannerClick = {
          navigator.goTo(
            AppFunctionalityStatusScreen(
              originScreen = screen
            )
          )
        }
      )
    )

    val functionalityStatus by remember {
      appFunctionalityService.status
    }.collectAsState()

    val cardsModel = CardListModel(
      cards = buildImmutableList {
        // Add hardware recovery status card
        hardwareRecoveryStatusCardUiStateMachine.model(
          HardwareRecoveryStatusCardUiProps(
            lostHardwareRecoveryData = screen.hardwareRecoveryData,
            onClick = {
              // TODO W-11181 Handle recovery status card click
            }
          )
        ).also { add(it) }

        // Add TC invitation cards
        recoveryContactCardsUiStateMachine.model(
          RecoveryContactCardsUiProps(
            onClick = {
              when (it) {
                is EndorsedTrustedContact -> {
                  // TODO W-11181 Handle endorsed contact click
                }
                is Invitation -> navigator.showSheet(
                  ViewInvitationSheet(
                    account = screen.account,
                    invitation = it,
                    origin = screen
                  )
                )
                is UnendorsedTrustedContact -> {
                  // TODO W-11181 Handle unendorsed contact click
                }
              }
            }
          )
        ).forEach(::add)
      }.filterNotNull().toImmutableList()
    )

    val firmwareUpdateData = firmwareDataService.firmwareData().value.firmwareUpdateState

    return SecurityHubBodyModel(
      isOffline = functionalityStatus is AppFunctionalityStatus.LimitedFunctionality,
      recommendations = recommendations.toImmutableList(),
      cardsModel = cardsModel,
      securityActions = securityActions,
      recoveryActions = recoveryActions,
      onRecommendationClick = {
        if (it.shouldShowEducation()) {
          navigator.goTo(
            screen = SecurityHubEducationScreen.RecommendationEducation(
              recommendation = it,
              originScreen = screen,
              firmwareData = firmwareUpdateData
            )
          )
        } else {
          navigator.navigateToScreen(it.navigationScreenId(), screen, firmwareUpdateData)
        }
      },
      onSecurityActionClick = {
        if (it.shouldShowEducation()) {
          navigator.goTo(
            screen = ActionEducation(
              action = it,
              originScreen = screen,
              firmwareData = firmwareUpdateData
            )
          )
        } else {
          navigator.navigateToScreen(it.navigationScreenId(), screen, firmwareUpdateData)
        }
      },
      onHomeTabClick = {
        navigator.exit()
      }
    ).asRootScreen(statusBannerModel = homeStatusBannerModel)
  }
}

private fun SecurityAction.shouldShowEducation(): Boolean {
  return type().hasEducation && requiresAction()
}

private fun SecurityActionRecommendation.shouldShowEducation(): Boolean {
  return actionType.hasEducation
}

fun Navigator.navigateToScreen(
  id: NavigationScreenId,
  originScreen: SecurityHubScreen,
  firmwareUpdateData: FirmwareData.FirmwareUpdateState,
) {
  when (id) {
    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS -> goTo(
      ManagingFingerprintsScreen(
        account = originScreen.account,
        onFwUpRequired = {
          // TODO W-11181 Handle firmware update required
        },
        entryPoint = EntryPoint.SECURITY_HUB,
        origin = originScreen
      )
    )
    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS -> goTo(
      TrustedContactManagementScreen(
        account = originScreen.account,
        inviteCode = null,
        onExit = { goTo(originScreen) }
      )
    )
    NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP,
    NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH,
    -> goTo(
      CloudBackupHealthDashboardScreen(account = originScreen.account, origin = originScreen)
    )
    NavigationScreenId.NAVIGATION_SCREEN_ID_UPDATE_FIRMWARE -> goTo(
      FwupScreen(
        firmwareUpdateData = firmwareUpdateData,
        onExit = { goTo(originScreen) }
      )
    )

    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS -> goTo(
      RecoveryChannelSettingsScreen(
        account = originScreen.account,
        source = Source.SecurityHub,
        origin = originScreen
      )
    )
    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC -> goTo(
      BiometricSettingScreen(
        fullAccount = originScreen.account,
        origin = originScreen
      )
    )
    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BITKEY_DEVICE -> goTo(
      screen = DeviceSettingsScreen(
        account = originScreen.account,
        lostHardwareRecoveryData = originScreen.hardwareRecoveryData,
        originScreen = originScreen
      )
    )
    else -> Router.route = Route.NavigationDeeplink(screen = id)
  }
}

fun SecurityAction.navigationScreenId(): NavigationScreenId =
  when (this.type()) {
    BIOMETRIC -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC
    CRITICAL_ALERTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
    EAK_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH
    FINGERPRINTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS
    INHERITANCE -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE
    MOBILE_KEY_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
    SOCIAL_RECOVERY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS
    HARDWARE_DEVICE -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BITKEY_DEVICE
  }

fun SecurityActionRecommendation.navigationScreenId(): NavigationScreenId =
  when (this) {
    BACKUP_MOBILE_KEY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
    BACKUP_EAK -> NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH
    ADD_FINGERPRINTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS
    ADD_TRUSTED_CONTACTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS
    ENABLE_CRITICAL_ALERTS, ENABLE_PUSH_NOTIFICATIONS, ENABLE_SMS_NOTIFICATIONS,
    ENABLE_EMAIL_NOTIFICATIONS,
    -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
    ADD_BENEFICIARY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE
    SETUP_BIOMETRICS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC
    PAIR_HARDWARE_DEVICE -> NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
    UPDATE_FIRMWARE -> NavigationScreenId.NAVIGATION_SCREEN_ID_UPDATE_FIRMWARE
  }
