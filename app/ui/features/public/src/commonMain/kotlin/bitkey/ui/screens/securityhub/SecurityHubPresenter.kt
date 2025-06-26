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
import bitkey.ui.sheets.ViewInvitationSheet
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
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
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiProps
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetStatusCardUiStateMachine
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiStateMachine
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheet
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.Source
import build.wallet.statemachine.status.AppFunctionalityStatusScreen
import build.wallet.statemachine.status.BannerContext
import build.wallet.statemachine.status.BannerType.OfflineStatus
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
  val initialState: SecurityHubUiState = SecurityHubUiState.ViewingSecurityHub,
) : Screen

@BitkeyInject(ActivityScope::class)
class SecurityHubPresenter(
  private val securityActionsService: SecurityActionsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val homeStatusBannerUiStateMachine: HomeStatusBannerUiStateMachine,
  private val firmwareDataService: FirmwareDataService,
  private val recoveryContactCardsUiStateMachine: RecoveryContactCardsUiStateMachine,
  private val hardwareRecoveryStatusCardUiStateMachine: HardwareRecoveryStatusCardUiStateMachine,
  private val fingerprintResetStatusCardUiStateMachine: FingerprintResetStatusCardUiStateMachine,
  private val resetFingerprintsUiStateMachine: ResetFingerprintsUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
  private val haptics: Haptics,
  private val fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
) : ScreenPresenter<SecurityHubScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: SecurityHubScreen,
  ): ScreenModel {
    val scope = rememberStableCoroutineScope()

    var uiState: SecurityHubUiState by remember {
      mutableStateOf(screen.initialState)
    }
    val onCannotUnlockFingerprints = {
      uiState = SecurityHubUiState.ResetFingerprintsState
    }

    var securityActions by remember { mutableStateOf(emptyList<SecurityAction>()) }
    var recoveryActions by remember { mutableStateOf(emptyList<SecurityAction>()) }

    val recommendationsWithStatus by remember {
      securityActionsService.getRecommendationsWithInteractionStatus()
    }.collectAsState(emptyList())

    val isFingerprintResetEnabled by fingerprintResetFeatureFlag.flagValue().collectAsState()

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
        bannerContext = BannerContext.SecurityHub,
        onBannerClick = {
          when (it) {
            OfflineStatus -> navigator.goTo(
              AppFunctionalityStatusScreen(
                originScreen = screen
              )
            )
            else -> {} // no-op since we are in Security Hub
          }
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
            account = screen.account,
            onClick = {
              Router.route = Route.NavigationDeeplink(
                screen = NavigationScreenId.NAVIGATION_SCREEN_ID_PAIR_DEVICE
              )
            }
          )
        ).also { add(it) }

        // Add Fingerprint Reset Status Card
        fingerprintResetStatusCardUiStateMachine.model(
          props = FingerprintResetStatusCardUiProps(
            account = screen.account,
            onClick = { actionId ->
              uiState = SecurityHubUiState.ResetFingerprintsState
            }
          )
        ).also { add(it) }

        // Add RC invitation cards
        recoveryContactCardsUiStateMachine.model(
          RecoveryContactCardsUiProps(
            onClick = {
              when (it) {
                is EndorsedTrustedContact -> {
                  navigator.goTo(
                    TrustedContactManagementScreen(
                      account = screen.account,
                      onExit = { navigator.goTo(screen) }
                    )
                  )
                }
                is Invitation -> navigator.showSheet(
                  ViewInvitationSheet(
                    account = screen.account,
                    invitation = it,
                    origin = screen
                  )
                )
                is UnendorsedTrustedContact -> {
                  navigator.goTo(
                    TrustedContactManagementScreen(
                      account = screen.account,
                      onExit = { navigator.goTo(screen) }
                    )
                  )
                }
              }
            }
          )
        ).forEach(::add)
      }.filterNotNull().toImmutableList()
    )

    val firmwareUpdateData by remember {
      firmwareDataService.firmwareData()
    }.collectAsState()

    // Mark all recommendations as viewed when Security Hub is entered
    LaunchedEffect("mark-all-recommendations-viewed") {
      securityActionsService.markAllRecommendationsViewed()
    }

    val recommendations = remember(recommendationsWithStatus) {
      recommendationsWithStatus.map { it.recommendation }.toImmutableList()
    }

    return when (uiState) {
      is SecurityHubUiState.ViewingSecurityHub -> {
        SecurityHubBodyModel(
          isOffline = functionalityStatus is AppFunctionalityStatus.LimitedFunctionality,
          // TODO W-11412 filter this in the service
          atRiskRecommendations = recommendations.filter {
            it == BACKUP_MOBILE_KEY || it == PAIR_HARDWARE_DEVICE
          }.toImmutableList(),
          // TODO W-11412 filter this in the service
          recommendations = recommendations.filter {
            it != BACKUP_MOBILE_KEY && it != PAIR_HARDWARE_DEVICE
          }.toImmutableList(),
          cardsModel = cardsModel,
          securityActions = securityActions,
          recoveryActions = recoveryActions,
          onRecommendationClick = { recommendation ->
            if (recommendation.shouldShowEducation()) {
              navigator.goTo(
                screen = SecurityHubEducationScreen.RecommendationEducation(
                  recommendation = recommendation,
                  originScreen = screen,
                  firmwareData = firmwareUpdateData.firmwareUpdateState
                )
              )
            } else {
              navigator.navigateToScreen(
                id = recommendation.navigationScreenId(),
                originScreen = screen,
                firmwareUpdateData = firmwareUpdateData.firmwareUpdateState,
                onCannotUnlockFingerprints = onCannotUnlockFingerprints
              )
            }
          },
          onSecurityActionClick = { securityAction ->
            navigator.navigateToScreen(
              id = securityAction.navigationScreenId(),
              originScreen = screen,
              firmwareUpdateData = firmwareUpdateData.firmwareUpdateState,
              isFingerprintResetEnabled = isFingerprintResetEnabled.value,
              onCannotUnlockFingerprints = onCannotUnlockFingerprints
            )
          },
          onHomeTabClick = {
            scope.launch {
              haptics.vibrate(effect = HapticsEffect.LightClick)
              navigator.exit()
            }
          }
        ).asRootScreen(statusBannerModel = homeStatusBannerModel)
      }

      is SecurityHubUiState.ResetFingerprintsState -> {
        resetFingerprintsUiStateMachine.model(
          props = ResetFingerprintsProps(
            onComplete = {
              uiState = SecurityHubUiState.ViewingSecurityHub
            },
            onCancel = {
              uiState = SecurityHubUiState.ViewingSecurityHub
            }
          )
        )
      }
    }
  }
}

private fun SecurityActionRecommendation.shouldShowEducation(): Boolean {
  return actionType.hasEducation
}

fun Navigator.navigateToScreen(
  id: NavigationScreenId,
  originScreen: SecurityHubScreen,
  firmwareUpdateData: FirmwareData.FirmwareUpdateState,
  isFingerprintResetEnabled: Boolean = false,
  onCannotUnlockFingerprints: (() -> Unit)? = null,
) {
  when (id) {
    NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS ->
      showSheet(
        ManageFingerprintsOptionsSheet(
          fingerprintResetEnabled = isFingerprintResetEnabled,
          onDismiss = {
            closeSheet()
          },
          onEditFingerprints = {
            onEditFingerprints(originScreen, firmwareUpdateData)
          },
          onCannotUnlock = {
            onCannotUnlock(onCannotUnlockFingerprints, originScreen)
          },
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

private fun Navigator.onCannotUnlock(
  onCannotUnlockFingerprints: (() -> Unit)?,
  originScreen: SecurityHubScreen,
) {
  if (onCannotUnlockFingerprints != null) {
    closeSheet()
    onCannotUnlockFingerprints()
  } else {
    goTo(
      SecurityHubScreen(
        account = originScreen.account,
        hardwareRecoveryData = originScreen.hardwareRecoveryData,
        initialState = SecurityHubUiState.ResetFingerprintsState
      )
    )
  }
}

private fun Navigator.onEditFingerprints(
  originScreen: SecurityHubScreen,
  firmwareUpdateData: FirmwareData.FirmwareUpdateState,
) {
  goTo(
    ManagingFingerprintsScreen(
      account = originScreen.account,
      onFwUpRequired = {
        goTo(
          FwupScreen(
            firmwareUpdateData = firmwareUpdateData,
            onExit = {
              goTo(
                SecurityHubScreen(
                  account = originScreen.account,
                  hardwareRecoveryData = originScreen.hardwareRecoveryData
                )
              )
            }
          )
        )
      },
      entryPoint = EntryPoint.SECURITY_HUB,
      origin = SecurityHubScreen(
        account = originScreen.account,
        hardwareRecoveryData = originScreen.hardwareRecoveryData
      )
    )
  )
}

fun SecurityAction.navigationScreenId(): NavigationScreenId =
  when (this.type()) {
    BIOMETRIC -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC
    CRITICAL_ALERTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS
    EEK_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH
    FINGERPRINTS -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS
    INHERITANCE -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE
    APP_KEY_BACKUP -> NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP
    SOCIAL_RECOVERY -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS
    HARDWARE_DEVICE -> NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BITKEY_DEVICE
    TRANSACTION_VERIFICATION -> NavigationScreenId.NAVIGATION_SCREEN_ID_TX_VERIFICATION_POLICY
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
    ENABLE_TRANSACTION_VERIFICATION -> NavigationScreenId.NAVIGATION_SCREEN_ID_TX_VERIFICATION_POLICY
  }

sealed interface SecurityHubUiState {
  /**
   * Viewing the main Security Hub screen
   */
  data object ViewingSecurityHub : SecurityHubUiState

  /**
   * State for resetting fingerprints
   */
  data object ResetFingerprintsState : SecurityHubUiState
}
