package build.wallet.statemachine.home.full

import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.InactiveApp
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SellBitcoinFeatureFlag
import build.wallet.limit.MobilePayServiceMock
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.input.SheetModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HomeUiStateMachineImplTests : FunSpec({

  val homeUiBottomSheetStateMachine =
    object : HomeUiBottomSheetStateMachine, StateMachineMock<HomeUiBottomSheetProps, SheetModel?>(
      null
    ) {}
  val currencyChangeMobilePayBottomSheetUpdater =
    CurrencyChangeMobilePayBottomSheetUpdaterMock(turbines::create)
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val appFunctionalityService = AppFunctionalityServiceFake()
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
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val sellBitcoinFeatureFlag = SellBitcoinFeatureFlag(featureFlagDao = FeatureFlagDaoFake())

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
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      appFunctionalityService = appFunctionalityService,
      expectedTransactionNoticeUiStateMachine = expectedTransactionNoticeUiStateMachine,
      deepLinkHandler = deepLinkHandler,
      inAppBrowserNavigator = inAppBrowserNavigator,
      clock = ClockFake(),
      timeZoneProvider = TimeZoneProviderMock(),
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      mobilePayService = mobilePayService,
      sellBitcoinFeatureFlag = sellBitcoinFeatureFlag
    )

  val props =
    HomeUiProps(
      account = FullAccountMock,
      lostHardwareRecoveryData = LostHardwareRecoveryDataMock
    )

  beforeEach {
    appFunctionalityService.reset()
    cloudBackupHealthRepository.reset()
    fiatCurrencyPreferenceRepository.reset()
    mobilePayService.reset()
    sellBitcoinFeatureFlag.reset()
    Router.reset()
  }

  suspend fun awaitSyncLoopCall() {
    cloudBackupHealthRepository.syncLoopCalls.awaitItem()
  }

  test("initial screen is money home") {
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()
    }
  }

  test("switch to settings tab") {
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        onSettings()
      }

      awaitScreenWithBodyModelMock<SettingsHomeUiProps>()
    }
  }

  test("homeUiBottomSheetStateMachine passes sheet to MoneyHome") {
    homeUiBottomSheetStateMachine.emitModel(SheetModelMock {})
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        homeBottomSheetModel.shouldNotBeNull()
      }
    }
  }

  test("homeUiBottomSheetStateMachine passes sheet to Settings") {
    homeUiBottomSheetStateMachine.emitModel(SheetModelMock {})
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

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
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      homeUiBottomSheetStateMachine.props.onShowSetSpendingLimitFlow()

      awaitScreenWithBodyModelMock<SpendingLimitProps>()
    }
  }

  test("change to currency re-calls currencyChangeMobilePayBottomSheetUpdater") {
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()

      fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = EUR
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()
    }
  }

  test("cloud backup health does not sync when app is inactive") {
    appFunctionalityService.status.emit(LimitedFunctionality(InactiveApp))

    stateMachine.test(props) {
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps>()
    }
  }

  test("partner sell app link does not invoke with feature flag disabled") {
    sellBitcoinFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    Router.route =
      Route.from("https://bitkey.world/links/app?context=partner_sale&event=transaction_created&source=MoonPay&event_id=01J91MGSEQ5JA0Q456ZQBN61D4")
    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        origin.shouldBe(MoneyHomeUiProps.Origin.Launch)
      }
    }
  }

  test("partner sell app link invokes with feature flag enabled") {
    sellBitcoinFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    Router.route =
      Route.from("https://bitkey.world/links/app?context=partner_sale&event=transaction_created&source=MoonPay&event_id=01J91MGSEQ5JA0Q456ZQBN61D4")

    stateMachine.test(props) {
      awaitSyncLoopCall()
      currencyChangeMobilePayBottomSheetUpdater.setOrClearHomeUiBottomSheetCalls.awaitItem()

      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        origin.shouldBe(MoneyHomeUiProps.Origin.Launch)
      }

      awaitScreenWithBodyModelMock<MoneyHomeUiProps> {
        inAppBrowserNavigator.onCloseCalls.awaitItem()

        origin.shouldBe(
          MoneyHomeUiProps.Origin.PartnershipsSell(
            partnerId = PartnerId("MoonPay"),
            event = PartnershipEvent("transaction_created"),
            partnerTransactionId = PartnershipTransactionId("01J91MGSEQ5JA0Q456ZQBN61D4")
          )
        )
      }
    }
  }
})
