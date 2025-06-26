package build.wallet.statemachine.home.full

import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.InactiveApp
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.SecurityHubFeatureFlag
import build.wallet.money.currency.USD
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.cloud.health.RepairAppKeyBackupProps
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.inheritance.InheritanceClaimNotificationUiProps
import build.wallet.statemachine.inheritance.InheritanceClaimNotificationUiStateMachine
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiStateMachine
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeProps
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiProps
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.RecoveryRelationshipNotificationUiProps
import build.wallet.statemachine.trustedcontact.RecoveryRelationshipNotificationUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.time.ClockFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.Instant

class HomeUiStateMachineImplTests : FunSpec({

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
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val partnershipsTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val appScope = TestScope()
  val securityHubFeatureFlag = SecurityHubFeatureFlag(FeatureFlagDaoFake())

  val stateMachine =
    HomeUiStateMachineImpl(
      homeStatusBannerUiStateMachine =
        object : HomeStatusBannerUiStateMachine,
          StateMachineMock<HomeStatusBannerUiProps, StatusBannerModel?>(
            initialModel = null
          ) {},
      moneyHomeUiStateMachine =
        object : MoneyHomeUiStateMachine, ScreenStateMachineMock<MoneyHomeUiProps>(
          "money-home"
        ) {},
      settingsHomeUiStateMachine =
        object : SettingsHomeUiStateMachine, ScreenStateMachineMock<SettingsHomeUiProps>(
          "settings"
        ) {},
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
      partnershipTransactionsService = partnershipsTransactionsService,
      inheritanceClaimNotificationUiStateMachine = object :
        InheritanceClaimNotificationUiStateMachine,
        ScreenStateMachineMock<InheritanceClaimNotificationUiProps>(
          "inheritance-claim-notifications"
        ) {},
      recoveryRelationshipNotificationUiStateMachine = object :
        RecoveryRelationshipNotificationUiStateMachine,
        ScreenStateMachineMock<RecoveryRelationshipNotificationUiProps>(
          "recovery-relationship-notifications"
        ) {},
      appCoroutineScope = appScope,
      navigatorPresenter = NavigatorPresenterFake(),
      repairCloudBackupStateMachine = object : RepairCloudBackupStateMachine,
        ScreenStateMachineMock<RepairAppKeyBackupProps>(
          "repair-cloud-backup"
        ) {}
    )

  val props =
    HomeUiProps(
      account = FullAccountMock,
      lostHardwareRecoveryData = LostHardwareRecoveryDataMock
    )

  beforeEach {
    appFunctionalityService.reset()
    cloudBackupHealthRepository.reset()
    Router.reset()
    partnershipsTransactionsService.reset()
    securityHubFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
  }

  suspend fun awaitSyncLoopCall() {
    cloudBackupHealthRepository.syncLoopCalls.awaitItem()
  }

  test("initial screen is money home") {
    stateMachine.test(props) {
      awaitSyncLoopCall()

      awaitBodyMock<MoneyHomeUiProps>()
    }
  }

  test("switch to settings tab") {
    stateMachine.test(props) {
      awaitSyncLoopCall()

      awaitBodyMock<MoneyHomeUiProps> {
        onSettings()
      }

      awaitBodyMock<SettingsHomeUiProps>()
    }
  }

  test("cloud backup health does not sync when app is inactive") {
    appFunctionalityService.status.emit(LimitedFunctionality(InactiveApp))

    stateMachine.test(props) {
      awaitBodyMock<MoneyHomeUiProps>()
    }
  }

  test("partner sell app link invokes") {
    partnershipsTransactionsService.transactions.value = listOf(
      PartnershipTransaction(
        id = PartnershipTransactionId("01J91MGSEQ5JA0Q456ZQBN61D4"),
        status = PartnershipTransactionStatus.PENDING,
        type = PartnershipTransactionType.SALE,
        context = null,
        partnerInfo = PartnerInfoFake,
        cryptoAmount = .1,
        txid = "txid",
        fiatAmount = 1000.0,
        fiatCurrency = USD.textCode,
        paymentMethod = null,
        created = Instant.DISTANT_PAST,
        updated = Instant.DISTANT_PAST,
        sellWalletAddress = null,
        partnerTransactionUrl = null
      )
    )

    Router.route =
      Route.from("https://bitkey.world/links/app?context=partner_sale&event=transaction_created&source=MoonPay&event_id=01J91MGSEQ5JA0Q456ZQBN61D4")

    stateMachine.test(props) {
      awaitSyncLoopCall()

      awaitBodyMock<MoneyHomeUiProps> {
        origin.shouldBe(MoneyHomeUiProps.Origin.Launch)
      }

      appScope.runCurrent()

      awaitBodyMock<MoneyHomeUiProps> {
        inAppBrowserNavigator.onCloseCalls.awaitItem()

        origin.shouldBe(
          MoneyHomeUiProps.Origin.PartnershipsSell(
            partnerId = PartnerId("MoonPay"),
            event = PartnershipEvent("transaction_created"),
            partnerTransactionId = PartnershipTransactionId("01J91MGSEQ5JA0Q456ZQBN61D4")
          )
        )
      }

      partnershipsTransactionsService.getCalls.awaitItem().shouldBe(PartnershipTransactionId("01J91MGSEQ5JA0Q456ZQBN61D4"))
    }
  }

  test("partner sell app link does not invoke when transaction is not found") {
    partnershipsTransactionsService.transactions.value = listOf(
      PartnershipTransaction(
        id = PartnershipTransactionId("db-id"),
        status = PartnershipTransactionStatus.PENDING,
        type = PartnershipTransactionType.SALE,
        context = null,
        partnerInfo = PartnerInfoFake,
        cryptoAmount = .1,
        txid = "txid",
        fiatAmount = 1000.0,
        fiatCurrency = USD.textCode,
        paymentMethod = null,
        created = Instant.DISTANT_PAST,
        updated = Instant.DISTANT_PAST,
        sellWalletAddress = null,
        partnerTransactionUrl = null
      )
    )

    Router.route =
      Route.from("https://bitkey.world/links/app?context=partner_sale&event=transaction_created&source=MoonPay&event_id=not-found-id")

    stateMachine.test(props) {
      awaitSyncLoopCall()

      appScope.runCurrent()

      awaitBodyMock<MoneyHomeUiProps> {
        origin.shouldBe(MoneyHomeUiProps.Origin.Launch)
      }

      inAppBrowserNavigator.onCloseCalls.awaitItem()
      partnershipsTransactionsService.getCalls.awaitItem().shouldBe(PartnershipTransactionId("not-found-id"))
    }
  }

  test("deep link routing for beneficiary invite") {
    Router.route = Route.BeneficiaryInvite("inviteCode")

    stateMachine.test(props) {
      awaitSyncLoopCall()
      awaitBodyMock<MoneyHomeUiProps> {
        origin.shouldBe(MoneyHomeUiProps.Origin.Launch)
      }

      awaitBodyMock<TrustedContactEnrollmentUiProps>("trusted-contact-enrollment") {
        inviteCode.shouldBe("inviteCode")
      }
    }
  }
})
