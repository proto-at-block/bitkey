package build.wallet.statemachine.home.full

import androidx.compose.runtime.*
import bitkey.securitycenter.SecurityActionsService
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.screens.securityhub.SecurityHubScreen
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.SecurityHubFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.limit.MobilePayService
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.partnerships.*
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.*
import build.wallet.statemachine.home.full.HomeScreen.MoneyHome
import build.wallet.statemachine.home.full.HomeScreen.Settings
import build.wallet.statemachine.home.full.PresentedScreen.*
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdater
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetProps
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachine
import build.wallet.statemachine.inheritance.InheritanceClaimNotificationUiProps
import build.wallet.statemachine.inheritance.InheritanceClaimNotificationUiStateMachine
import build.wallet.statemachine.inheritance.InheritanceNotificationAction
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiProps.Origin
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachine
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeProps
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiProps
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.SettingsListState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.SettingsListState.ShowingInheritanceUiState
import build.wallet.statemachine.status.AppFunctionalityStatusUiProps
import build.wallet.statemachine.status.AppFunctionalityStatusUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.*
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class HomeUiStateMachineImpl(
  private val appFunctionalityStatusUiStateMachine: AppFunctionalityStatusUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyChangeMobilePayBottomSheetUpdater: CurrencyChangeMobilePayBottomSheetUpdater,
  private val homeStatusBannerUiStateMachine: HomeStatusBannerUiStateMachine,
  private val homeUiBottomSheetStateMachine: HomeUiBottomSheetStateMachine,
  private val moneyHomeUiStateMachine: MoneyHomeUiStateMachine,
  private val settingsHomeUiStateMachine: SettingsHomeUiStateMachine,
  private val setSpendingLimitUiStateMachine: SetSpendingLimitUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val expectedTransactionNoticeUiStateMachine: ExpectedTransactionNoticeUiStateMachine,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val appFunctionalityService: AppFunctionalityService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val deepLinkHandler: DeepLinkHandler,
  private val clock: Clock,
  private val timeZoneProvider: TimeZoneProvider,
  private val mobilePayService: MobilePayService,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val inheritanceClaimNotificationUiStateMachine:
    InheritanceClaimNotificationUiStateMachine,
  private val recoveryRelationshipNotificationUiStateMachine:
    RecoveryRelationshipNotificationUiStateMachine,
  private val appCoroutineScope: CoroutineScope,
  private val navigatorPresenter: NavigatorPresenter,
  private val securityHubFeatureFlag: SecurityHubFeatureFlag,
  private val securityActionsService: SecurityActionsService,
) : HomeUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: HomeUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf(
        HomeUiState(
          rootScreen = MoneyHome(origin = Origin.Launch),
          presentedScreen = null
        )
      )
    }

    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()
    val mobilePayData by remember { mobilePayService.mobilePayData }.collectAsState()
    val recommendations by remember { securityActionsService.getRecommendations() }
      .collectAsState(emptyList())

    // Update bottom sheet for currency changes which affect Mobile Pay
    // Set up an effect to set or clear the bottom sheet alert when Mobile Pay is enabled
    val fiatCurrency by remember {
      fiatCurrencyPreferenceRepository.fiatCurrencyPreference
    }.collectAsState()

    LaunchedEffect("set-or-clear-bottom-sheet", fiatCurrency) {
      // TODO(W-8080): implement as a worker
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheet(
        fiatCurrency = fiatCurrency,
        mobilePayData = mobilePayData
      )
    }

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite -> {
            uiState = uiState.copy(presentedScreen = AddTrustedContact(route.inviteCode))
            return@onRouteChange true
          }
          is Route.BeneficiaryInvite -> {
            uiState = uiState.copy(presentedScreen = BecomeBeneficiary(route.inviteCode))
            return@onRouteChange true
          }
          is Route.PartnerTransferDeeplink -> {
            // Close any in-app browser if open
            // this can happen when a deeplink is triggered from an in-app browser
            inAppBrowserNavigator.close()
            uiState = uiState.copy(
              presentedScreen = PartnerTransfer(
                partner = route.partner?.let(::PartnerId),
                event = route.event?.let(::PartnershipEvent),
                partnerTransactionId = route.partnerTransactionId?.let(::PartnershipTransactionId)
              )
            )
            return@onRouteChange true
          }
          is Route.PartnerSaleDeeplink -> {
            // Close any in-app browser if open
            // this can happen when a deeplink is triggered from an in-app browser
            inAppBrowserNavigator.close()
            route.partnerTransactionId?.let { transactionId ->
              /**
               * This uses [appCoroutineScope] to launch over the parent [LaunchedEffect]'s scope in the
               * event that the scope is cancelled due to being removed from composition
               */
              appCoroutineScope.launch {
                val partnershipTransactionId = PartnershipTransactionId(transactionId)
                // Before acting on the deeplink, verify that the transaction exists in the local database
                // to prevent potential spoofing-attacks
                partnershipTransactionsService.getTransactionById(
                  transactionId = partnershipTransactionId
                ).get()
                  ?.let {
                    uiState = uiState.copy(
                      rootScreen = MoneyHome(
                        origin = Origin.PartnershipsSell(
                          partnerId = route.partner?.let(::PartnerId),
                          event = route.event?.let(::PartnershipEvent),
                          partnerTransactionId = partnershipTransactionId
                        )
                      )
                    )
                  }
              }
            }
            return@onRouteChange true
          }
          is Route.NavigationDeeplink -> {
            return@onRouteChange when (route.screen) {
              NavigationScreenId.NAVIGATION_SCREEN_ID_MONEY_HOME -> {
                uiState = uiState.copy(rootScreen = MoneyHome(origin = Origin.Launch))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_SETTINGS -> {
                uiState = uiState.copy(rootScreen = Settings(null))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE -> {
                uiState = uiState.copy(rootScreen = Settings(ShowingInheritanceUiState(ManagingInheritanceTab.Beneficiaries)))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE_BENEFACTOR -> {
                uiState = uiState.copy(rootScreen = Settings(ShowingInheritanceUiState(ManagingInheritanceTab.Inheritance)))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingBiometricSettingUiState))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingRecoveryChannelsUiState))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingCloudBackupHealthUiState))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingBitkeyDeviceSettingsUiState))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingCloudBackupHealthUiState))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS -> {
                uiState = uiState.copy(rootScreen = Settings(SettingsListState.ShowingTrustedContactsUiState))
                true
              }
              else -> false
            }
          }
          is Route.RecoveryRelationshipNavigationDeepLink -> {
            return@onRouteChange when (route.screen) {
              NavigationScreenId.NAVIGATION_SCREEN_ID_INHERITANCE_BENEFACTOR_INVITE_ACCEPTED -> {
                uiState = uiState.copy(presentedScreen = RecoveryRelationshipAction(RecoveryRelationshipNotificationAction.BenefactorInviteAccepted, route.recoveryRelationshipId))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_SOCIAL_RECOVERY_PROTECTED_CUSTOMER_INVITE_ACCEPTED -> {
                uiState = uiState.copy(presentedScreen = RecoveryRelationshipAction(RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted, route.recoveryRelationshipId))
                true
              }

              else -> false
            }
          }
          is Route.InheritanceClaimNavigationDeeplink -> {
            return@onRouteChange when (route.screen) {
              NavigationScreenId.NAVIGATION_SCREEN_ID_INHERITANCE_DECLINE_CLAIM -> {
                uiState = uiState.copy(presentedScreen = InheritanceClaimAction(InheritanceNotificationAction.DenyClaim, route.claimId))
                true
              }
              NavigationScreenId.NAVIGATION_SCREEN_ID_INHERITANCE_COMPLETE_CLAIM -> {
                uiState = uiState.copy(presentedScreen = InheritanceClaimAction(InheritanceNotificationAction.CompleteClaim, route.claimId))
                true
              }

              else -> false
            }
          }
        }
      }
    }

    if (appFunctionalityStatus.featureStates.cloudBackupHealth == Available && props.account is FullAccount) {
      LaunchedEffect("sync-cloud-backup-health") {
        // TODO: W-9117 - migrate to app worker pattern
        withContext(Dispatchers.Default) {
          cloudBackupHealthRepository.syncLoop(account = props.account)
        }
      }
    }

    return when (val presentedScreen = uiState.presentedScreen) {
      null -> {
        // Observe the global bottom sheet model
        val homeBottomSheetModel = homeUiBottomSheetStateMachine.model(
          props = HomeUiBottomSheetProps(
            onShowSetSpendingLimitFlow = {
              uiState = uiState.copy(presentedScreen = SetSpendingLimit)
            }
          )
        )

        // Observe the global status banner model
        val homeStatusBannerModel = homeStatusBannerUiStateMachine.model(
          props = HomeStatusBannerUiProps(
            onBannerClick = { limitedFunctionality ->
              uiState = uiState.copy(presentedScreen = AppFunctionalityStatus(limitedFunctionality))
            }
          )
        )

        when (val rootScreen = uiState.rootScreen) {
          is MoneyHome -> moneyHomeUiStateMachine.model(
            props = MoneyHomeUiProps(
              account = props.account,
              lostHardwareRecoveryData = props.lostHardwareRecoveryData,
              homeBottomSheetModel = homeBottomSheetModel,
              homeStatusBannerModel = homeStatusBannerModel,
              onSettings = {
                uiState = uiState.copy(rootScreen = Settings(null))
              },
              origin = rootScreen.origin,
              onPartnershipsWebFlowCompleted = { partnerInfo, transaction ->
                uiState = uiState.copy(
                  presentedScreen = PartnerTransfer(
                    partner = partnerInfo.partnerId,
                    event = PartnershipEvent.WebFlowCompleted,
                    partnerTransactionId = transaction.id
                  )
                )
              },
              tabs = if (securityHubFeatureFlag.isEnabled()) {
                listOf(
                  HomeTab.MoneyHome(selected = true, onSelected = {
                    uiState = uiState.copy(rootScreen = MoneyHome(origin = Origin.Launch))
                  }),
                  HomeTab.SecurityHub(
                    selected = false,
                    onSelected = {
                      uiState = uiState.copy(rootScreen = HomeScreen.SecurityHub)
                    },
                    badged = recommendations.isNotEmpty()
                  )
                )
              } else {
                emptyList()
              }
            )
          )

          is Settings -> settingsHomeUiStateMachine.model(
            props = SettingsHomeUiProps(
              onBack = {
                uiState = uiState.copy(rootScreen = MoneyHome(origin = Origin.Settings))
              },
              account = props.account,
              settingsListState = rootScreen.screen,
              lostHardwareRecoveryData = props.lostHardwareRecoveryData,
              homeBottomSheetModel = homeBottomSheetModel,
              homeStatusBannerModel = homeStatusBannerModel
            )
          )
          HomeScreen.SecurityHub -> navigatorPresenter.model(
            SecurityHubScreen(
              homeStatusBannerModel = homeStatusBannerModel,
              tabs = if (securityHubFeatureFlag.isEnabled()) {
                listOf(
                  HomeTab.MoneyHome(selected = false, onSelected = {
                    uiState = uiState.copy(rootScreen = MoneyHome(origin = Origin.Launch))
                  }),
                  HomeTab.SecurityHub(
                    selected = true,
                    onSelected = {
                      uiState = uiState.copy(rootScreen = HomeScreen.SecurityHub)
                    },
                    badged = false
                  )
                )
              } else {
                emptyList()
              }
            ),
            onExit = {
              uiState = uiState.copy(rootScreen = MoneyHome(origin = Origin.Launch))
            }
          )
        }
      }

      SetSpendingLimit -> setSpendingLimitUiStateMachine.model(
        props = SpendingLimitProps(
          // This is always null here because we are setting a limit after a currency change
          // (so the old limit is a different currency and cannot be used as a starting point).
          currentSpendingLimit = null,
          onClose = { uiState = uiState.copy(presentedScreen = null) },
          onSetLimit = { uiState = uiState.copy(presentedScreen = null) },
          account = props.account as FullAccount
        )
      )

      is AddTrustedContact -> trustedContactEnrollmentUiStateMachine.model(
        props = TrustedContactEnrollmentUiProps(
          retreat = Retreat(
            style = RetreatStyle.Close,
            onRetreat = {
              uiState = uiState.copy(presentedScreen = null)
            }
          ),
          account = props.account,
          inviteCode = presentedScreen.inviteCode,
          onDone = {
            uiState = uiState.copy(presentedScreen = null)
          },
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          variant = TrustedContactFeatureVariant.Direct(
            target = TrustedContactFeatureVariant.Feature.Recovery
          )
        )
      )
      is BecomeBeneficiary -> trustedContactEnrollmentUiStateMachine.model(
        props = TrustedContactEnrollmentUiProps(
          retreat = Retreat(
            style = RetreatStyle.Close,
            onRetreat = {
              uiState = uiState.copy(presentedScreen = null)
            }
          ),
          account = props.account,
          inviteCode = presentedScreen.inviteCode,
          onDone = { uiState = uiState.copy(presentedScreen = null) },
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          variant = TrustedContactFeatureVariant.Direct(
            target = TrustedContactFeatureVariant.Feature.Inheritance
          )
        )
      )

      is PartnerTransfer -> expectedTransactionNoticeUiStateMachine.model(
        props = ExpectedTransactionNoticeProps(
          account = props.account,
          partner = presentedScreen.partner,
          event = presentedScreen.event,
          partnerTransactionId = presentedScreen.partnerTransactionId,
          receiveTime = clock.now().toLocalDateTime(timeZoneProvider.current()),
          onViewInPartnerApp = { method ->
            when (method) {
              is PartnerRedirectionMethod.Deeplink -> deepLinkHandler.openDeeplink(
                url = method.urlString,
                appRestrictions = null
              )
              is PartnerRedirectionMethod.Web -> {
                uiState = uiState.copy(presentedScreen = InAppBrowser(url = method.urlString))
              }
            }
          },
          onBack = {
            uiState = uiState.copy(presentedScreen = null)
          }
        )
      )

      is InAppBrowser -> InAppBrowserModel(
        open = {
          inAppBrowserNavigator.open(
            url = presentedScreen.url,
            onClose = { uiState = uiState.copy(presentedScreen = null) }
          )
        }
      ).asModalScreen()

      is AppFunctionalityStatus -> appFunctionalityStatusUiStateMachine.model(
        props = AppFunctionalityStatusUiProps(
          onClose = { uiState = uiState.copy(presentedScreen = null) },
          status = presentedScreen.status
        )
      )

      is InheritanceClaimAction -> inheritanceClaimNotificationUiStateMachine.model(
        props = InheritanceClaimNotificationUiProps(
          fullAccount = props.account as FullAccount,
          claimId = presentedScreen.claimId,
          action = presentedScreen.action,
          onBack = { uiState = uiState.copy(presentedScreen = null) }
        )
      )

      is RecoveryRelationshipAction -> recoveryRelationshipNotificationUiStateMachine.model(
        props = RecoveryRelationshipNotificationUiProps(
          fullAccount = props.account as FullAccount,
          action = presentedScreen.action,
          recoveryRelationshipId = presentedScreen.recoveryRelationshipId,
          onBack = { uiState = uiState.copy(presentedScreen = null) }
        )
      )
    }
  }
}

private data class HomeUiState(
  val rootScreen: HomeScreen,
  val presentedScreen: PresentedScreen?,
)

/**
 * Represents a specific "home" screen. These are special screens managed by this state
 * machine that share the ability to present certain content, [PresentedScreen]
 */
private sealed interface HomeScreen {
  /**
   * Indicates that money home is shown.
   */
  data class MoneyHome(val origin: Origin) : HomeScreen

  /**
   * Indicates that settings are shown.
   */
  data class Settings(val screen: SettingsListState?) : HomeScreen

  /**
   * Indicates that the security hub is shown.
   */
  data object SecurityHub : HomeScreen
}

/**
 * Represents a screen presented on top of either [HomeScreen]
 */
private sealed interface PresentedScreen {
  /** Indicates that the set spending limit flow is currently presented */
  data object SetSpendingLimit : PresentedScreen

  /** Indicates that the app functionality status screen is currently presented */
  data class AppFunctionalityStatus(val status: LimitedFunctionality) : PresentedScreen

  /** Indicates that the add trusted contact flow is currently presented */
  data class AddTrustedContact(val inviteCode: String?) : PresentedScreen

  /** Indicates that the become beneficiary flow is currently presented */
  data class BecomeBeneficiary(
    val inviteCode: String?,
  ) : PresentedScreen

  /** Perform an action on an inheritance claim */
  data class InheritanceClaimAction(
    val action: InheritanceNotificationAction,
    val claimId: String,
  ) : PresentedScreen

  /** Perform an action on a recovery relationship */
  data class RecoveryRelationshipAction(
    val action: RecoveryRelationshipNotificationAction,
    val recoveryRelationshipId: String,
  ) : PresentedScreen

  /** Indicates that the partner transfer flow is currently presented */
  data class PartnerTransfer(
    val partner: PartnerId?,
    val event: PartnershipEvent?,
    val partnerTransactionId: PartnershipTransactionId?,
  ) : PresentedScreen

  /** Indicates that an in-app browser is currently being displayed. */
  data class InAppBrowser(
    val url: String,
  ) : PresentedScreen
}
