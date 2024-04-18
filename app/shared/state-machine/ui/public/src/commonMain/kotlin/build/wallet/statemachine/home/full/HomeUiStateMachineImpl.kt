package build.wallet.statemachine.home.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AppFunctionalityStatusProvider
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.availability.InactiveApp
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.money.FiatMoney
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.recovery.socrec.SocRecProtectedCustomerActions
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.home.full.HomeScreen.MoneyHome
import build.wallet.statemachine.home.full.HomeScreen.Settings
import build.wallet.statemachine.home.full.PresentedScreen.AppFunctionalityStatus
import build.wallet.statemachine.home.full.PresentedScreen.SetSpendingLimit
import build.wallet.statemachine.home.full.bottomsheet.CurrencyChangeMobilePayBottomSheetUpdater
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetProps
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachine
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiProps
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachine
import build.wallet.statemachine.status.AppFunctionalityStatusUiProps
import build.wallet.statemachine.status.AppFunctionalityStatusUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.ui.model.status.StatusBannerModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

class HomeUiStateMachineImpl(
  private val appFunctionalityStatusUiStateMachine: AppFunctionalityStatusUiStateMachine,
  private val currencyChangeMobilePayBottomSheetUpdater: CurrencyChangeMobilePayBottomSheetUpdater,
  private val currencyConverter: CurrencyConverter,
  private val homeStatusBannerUiStateMachine: HomeStatusBannerUiStateMachine,
  private val homeUiBottomSheetStateMachine: HomeUiBottomSheetStateMachine,
  private val moneyHomeUiStateMachine: MoneyHomeUiStateMachine,
  private val settingsHomeUiStateMachine: SettingsHomeUiStateMachine,
  private val setSpendingLimitUiStateMachine: SetSpendingLimitUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val appFunctionalityStatusProvider: AppFunctionalityStatusProvider,
) : HomeUiStateMachine {
  @Composable
  override fun model(props: HomeUiProps): ScreenModel {
    val fiatCurrency = props.currencyPreferenceData.fiatCurrencyPreference

    var uiState by remember {
      mutableStateOf(
        HomeUiState(
          convertedFiatBalance = FiatMoney.zero(fiatCurrency),
          rootScreen = MoneyHome(origin = MoneyHomeUiProps.Origin.Launch),
          presentedScreen = null
        )
      )
    }

    val appFunctionalityStatus =
      remember {
        appFunctionalityStatusProvider
          .appFunctionalityStatus(
            props.accountData.account.config.f8eEnvironment
          )
      }.collectAsState(LimitedFunctionality(InactiveApp)).value

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite -> {
            uiState =
              uiState.copy(
                presentedScreen = PresentedScreen.AddTrustedContact(route.inviteCode)
              )
            return@onRouteChange true
          }
          else -> false
        }
      }
    }

    // Observe the global bottom sheet model
    val homeBottomSheetModel =
      homeUiBottomSheetStateMachine.model(
        props =
          HomeUiBottomSheetProps(
            fiatCurrency = fiatCurrency,
            mobilePayData = props.accountData.mobilePayData,
            onShowSetSpendingLimitFlow = {
              uiState = uiState.copy(presentedScreen = SetSpendingLimit)
            }
          )
      )

    // Observe the global status banner model
    val homeStatusBannerModel =
      homeStatusBannerUiStateMachine.model(
        props =
          HomeStatusBannerUiProps(
            f8eEnvironment = props.accountData.account.config.f8eEnvironment,
            onBannerClick = { limitedFunctionality ->
              uiState = uiState.copy(presentedScreen = AppFunctionalityStatus(limitedFunctionality))
            }
          )
      )

    // Update bottom sheet for currency changes which affect Mobile Pay
    // Set up an effect to set or clear the bottom sheet alert when Mobile Pay is enabled
    val mobilePayData = props.accountData.mobilePayData
    LaunchedEffect("set-or-clear-bottom-sheet", fiatCurrency) {
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheet(
        fiatCurrency = fiatCurrency,
        mobilePayData = mobilePayData
      )
    }

    if (appFunctionalityStatus.featureStates.cloudBackupHealth == Available) {
      SyncCloudBackupHealthEffect(props)
    }

    SyncFiatBalanceEquivalentEffect(
      props
    ) { fiatBalance ->
      uiState = uiState.copy(convertedFiatBalance = fiatBalance)
    }

    val socRecRelationships = SyncRelationshipsEffect(props.accountData.account)
    val socRecActions = socRecRelationshipsRepository.toActions(props.accountData.account)

    return when (val presentedScreen = uiState.presentedScreen) {
      null ->
        when (val rootScreen = uiState.rootScreen) {
          is MoneyHome ->
            MoneyHomeScreenModel(
              props = props,
              state = uiState,
              socRecRelationships = socRecRelationships,
              socRecActions = socRecActions,
              homeBottomSheetModel = homeBottomSheetModel,
              homeStatusBannerModel = homeStatusBannerModel,
              onSettingsButtonClicked = {
                uiState = uiState.copy(rootScreen = Settings)
              },
              origin = rootScreen.origin
            )

          Settings ->
            SettingsScreenModel(
              props = props,
              socRecRelationships = socRecRelationships,
              socRecActions = socRecActions,
              homeBottomSheetModel = homeBottomSheetModel,
              homeStatusBannerModel = homeStatusBannerModel,
              onBack = {
                uiState = uiState.copy(rootScreen = MoneyHome(origin = MoneyHomeUiProps.Origin.Settings))
              }
            )
        }

      SetSpendingLimit ->
        setSpendingLimitUiStateMachine.model(
          props =
            SpendingLimitProps(
              // This is always null here because we are setting a limit after a currency change
              // (so the old limit is a different currency and cannot be used as a starting point).
              currentSpendingLimit = null,
              onClose = { uiState = uiState.copy(presentedScreen = null) },
              onSetLimit = { uiState = uiState.copy(presentedScreen = null) },
              fiatCurrency = fiatCurrency,
              accountData = props.accountData
            )
        )

      is PresentedScreen.AddTrustedContact ->
        trustedContactEnrollmentUiStateMachine.model(
          props =
            TrustedContactEnrollmentUiProps(
              retreat =
                Retreat(
                  style = RetreatStyle.Close,
                  onRetreat = {
                    uiState = uiState.copy(presentedScreen = null)
                  }
                ),
              account = props.accountData.account,
              inviteCode = presentedScreen.inviteCode,
              acceptInvitation = socRecActions::acceptInvitation,
              retrieveInvitation = socRecActions::retrieveInvitation,
              onDone = {
                uiState = uiState.copy(presentedScreen = null)
              },
              screenPresentationStyle = ScreenPresentationStyle.Modal
            )
        )

      is AppFunctionalityStatus ->
        appFunctionalityStatusUiStateMachine.model(
          props =
            AppFunctionalityStatusUiProps(
              onClose = { uiState = uiState.copy(presentedScreen = null) },
              status = presentedScreen.status
            )
        )
    }
  }

  @Composable
  private fun MoneyHomeScreenModel(
    props: HomeUiProps,
    state: HomeUiState,
    socRecRelationships: SocRecRelationships,
    socRecActions: SocRecProtectedCustomerActions,
    homeBottomSheetModel: SheetModel?,
    homeStatusBannerModel: StatusBannerModel?,
    onSettingsButtonClicked: () -> Unit,
    origin: MoneyHomeUiProps.Origin,
  ) = moneyHomeUiStateMachine.model(
    props =
      MoneyHomeUiProps(
        accountData = props.accountData,
        firmwareData = props.firmwareData,
        convertedFiatBalance = state.convertedFiatBalance,
        fiatCurrency = props.currencyPreferenceData.fiatCurrencyPreference,
        socRecRelationships = socRecRelationships,
        socRecActions = socRecActions,
        homeBottomSheetModel = homeBottomSheetModel,
        homeStatusBannerModel = homeStatusBannerModel,
        onSettings = onSettingsButtonClicked,
        origin = origin
      )
  )

  @Composable
  private fun SyncFiatBalanceEquivalentEffect(
    props: HomeUiProps,
    onFiatBalanceSynced: (FiatMoney) -> Unit,
  ) {
    LaunchedEffect(
      "sync-fiat-equivalent-balance",
      props.accountData.transactionsData.balance.total,
      props.currencyPreferenceData.fiatCurrencyPreference
    ) {
      val convertedFiatBalance =
        currencyConverter
          .convert(
            fromAmount = props.accountData.transactionsData.balance.total,
            toCurrency = props.currencyPreferenceData.fiatCurrencyPreference,
            atTime = null
          ).filterNotNull().firstOrNull() as? FiatMoney
          ?: FiatMoney.zero(props.currencyPreferenceData.fiatCurrencyPreference)

      onFiatBalanceSynced(convertedFiatBalance)
    }
  }

  @Composable
  private fun SyncRelationshipsEffect(account: FullAccount): SocRecRelationships {
    LaunchedEffect(account) {
      socRecRelationshipsRepository.syncLoop(scope = this, account)
    }

    return remember {
      socRecRelationshipsRepository
        .relationships
        .filterNotNull()
    }.collectAsState(SocRecRelationships.EMPTY).value
  }

  @Composable
  private fun SyncCloudBackupHealthEffect(props: HomeUiProps) {
    LaunchedEffect("sync-cloud-backup-health") {
      cloudBackupHealthRepository.syncLoop(scope = this, account = props.accountData.account)
    }
  }

  @Composable
  private fun SettingsScreenModel(
    props: HomeUiProps,
    homeBottomSheetModel: SheetModel?,
    homeStatusBannerModel: StatusBannerModel?,
    onBack: () -> Unit,
    socRecRelationships: SocRecRelationships,
    socRecActions: SocRecProtectedCustomerActions,
  ) = settingsHomeUiStateMachine.model(
    props =
      SettingsHomeUiProps(
        onBack = onBack,
        accountData = props.accountData,
        electrumServerData = props.electrumServerData,
        firmwareData = props.firmwareData,
        currencyPreferenceData = props.currencyPreferenceData,
        socRecRelationships = socRecRelationships,
        socRecActions = socRecActions,
        homeBottomSheetModel = homeBottomSheetModel,
        homeStatusBannerModel = homeStatusBannerModel
      )
  )
}

private data class HomeUiState(
  val convertedFiatBalance: FiatMoney,
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
  data class MoneyHome(val origin: MoneyHomeUiProps.Origin) : HomeScreen

  /**
   * Indicates that settings are shown.
   */
  data object Settings : HomeScreen
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
}
