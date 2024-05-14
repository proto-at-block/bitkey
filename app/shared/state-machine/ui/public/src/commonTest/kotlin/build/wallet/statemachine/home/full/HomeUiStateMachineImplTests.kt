package build.wallet.statemachine.home.full

import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AppFunctionalityStatusProviderMock
import build.wallet.availability.InactiveApp
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.socrec.SocRecRelationshipsFake
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.input.SheetModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.sync.PlaceholderElectrumServerDataMock
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetProps
import build.wallet.statemachine.home.full.bottomsheet.HomeUiBottomSheetStateMachine
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachine
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeProps
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiProps
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachine
import build.wallet.statemachine.status.AppFunctionalityStatusUiProps
import build.wallet.statemachine.status.AppFunctionalityStatusUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.time.ClockFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull

class HomeUiStateMachineImplTests : FunSpec({

  val homeUiBottomSheetStateMachine =
    object : HomeUiBottomSheetStateMachine, StateMachineMock<HomeUiBottomSheetProps, SheetModel?>(
      null
    ) {}
  val currencyChangeMobilePayBottomSheetUpdater =
    CurrencyChangeMobilePayBottomSheetUpdaterMock(turbines::create)
  val socRecRelationshipsRepositoryMock = SocRecRelationshipsRepositoryMock(turbines::create)
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val appFunctionalityStatusProvider = AppFunctionalityStatusProviderMock()
  val expectedTransactionNoticeUiStateMachine = object : ExpectedTransactionNoticeUiStateMachine,
    ScreenStateMachineMock<ExpectedTransactionNoticeProps>(
      "expected-transaction-notice"
    ) {}

  val deepLinkCalls = turbines.create<String>("Deep Links")
  val deepLinkHandler = object : DeepLinkHandler {
    override fun openDeeplink(
      url: String,
      appRestrictions: AppRestrictions?,
    ): OpenDeeplinkResult {
      deepLinkCalls.add(url)

      return OpenDeeplinkResult.Opened(OpenDeeplinkResult.AppRestrictionResult.Success)
    }
  }

  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine =
    HomeUiStateMachineImpl(
      appFunctionalityStatusUiStateMachine =
        object : AppFunctionalityStatusUiStateMachine,
          ScreenStateMachineMock<AppFunctionalityStatusUiProps>(
            "app-status"
          ) {},
      homeStatusBannerUiStateMachine =
        object : HomeStatusBannerUiStateMachine,
          StateMachineMock<HomeStatusBannerUiProps, StatusBannerModel?>(
            initialModel = null
          ) {},
      homeUiBottomSheetStateMachine = homeUiBottomSheetStateMachine,
      moneyHomeUiStateMachine =
        object : MoneyHomeUiStateMachine, ScreenStateMachineMock<MoneyHomeUiProps>(
          "money-home"
        ) {},
      settingsHomeUiStateMachine =
        object : SettingsHomeUiStateMachine, ScreenStateMachineMock<SettingsHomeUiProps>(
          "settings"
        ) {},
      currencyChangeMobilePayBottomSheetUpdater = currencyChangeMobilePayBottomSheetUpdater,
      setSpendingLimitUiStateMachine =
        object : SetSpendingLimitUiStateMachine, ScreenStateMachineMock<SpendingLimitProps>(
          "set-spending-limit"
        ) {},
      trustedContactEnrollmentUiStateMachine =
        object : TrustedContactEnrollmentUiStateMachine,
          ScreenStateMachineMock<TrustedContactEnrollmentUiProps>(
            "trusted-contact-enrollment"
          ) {},
      socRecRelationshipsRepository = socRecRelationshipsRepositoryMock,
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      expectedTransactionNoticeUiStateMachine = expectedTransactionNoticeUiStateMachine,
      deepLinkHandler = deepLinkHandler,
      inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
      clock = ClockFake(),
      timeZoneProvider = TimeZoneProviderMock(),
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )

  val props =
    HomeUiProps(
      accountData = ActiveKeyboxLoadedDataMock,
      electrumServerData = PlaceholderElectrumServerDataMock,
      firmwareData = FirmwareDataUpToDateMock
    )

  beforeEach {
    appFunctionalityStatusProvider.reset()
    cloudBackupHealthRepository.reset()
    fiatCurrencyPreferenceRepository.reset()
  }

  afterTest {
    socRecRelationshipsRepositoryMock.launchSyncCalls.awaitItem()
  }

  suspend fun awaitSyncLoopCall() {
    cloudBackupHealthRepository.syncLoopCalls.awaitItem()
  }

  test("initial screen is money home") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()
    }
  }

  test("switch to settings tab") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        onSettings()
      }

      awaitScreenWithBodyModelMock<SettingsHomeUiProps>()
    }
  }

  test("sync social recovery relationships") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        onSettings()
      }

      awaitScreenWithBodyModelMock<SettingsHomeUiProps> {
        socRecRelationships.shouldBeEqual(SocRecRelationshipsFake)
      }
    }
  }

  test("homeUiBottomSheetStateMachine passes sheet to MoneyHome") {
    homeUiBottomSheetStateMachine.emitModel(SheetModelMock {})
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        homeBottomSheetModel.shouldNotBeNull()
      }
    }
  }

  test("homeUiBottomSheetStateMachine passes sheet to Settings") {
    homeUiBottomSheetStateMachine.emitModel(SheetModelMock {})
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        onSettings()
      }
      awaitScreenWithBodyModelMock<SettingsHomeUiProps> {
        homeBottomSheetModel.shouldNotBeNull()
      }
    }
  }

  test("homeUiBottomSheetStateMachine onShowSetSpendingLimitFlow presents screen") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      homeUiBottomSheetStateMachine.props.onShowSetSpendingLimitFlow()

      awaitScreenWithBodyModelMock<SpendingLimitProps>()
    }
  }

  test("change to currency re-calls currencyChangeMobilePayBottomSheetUpdater") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(FullFunctionality)
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-app functionality status check
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = EUR
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()
    }
  }

  test("cloud backup health does not sync when app is inactive") {
    stateMachine.test(props) {
      appFunctionalityStatusProvider.appFunctionalityStatusFlow
        .emit(LimitedFunctionality(InactiveApp))
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      // Pre-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      // Post-currency conversion
      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()
    }
  }
})
